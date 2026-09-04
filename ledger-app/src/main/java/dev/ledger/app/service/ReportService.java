package dev.ledger.app.service;

import dev.ledger.app.config.CurrentUser;
import dev.ledger.app.repo.TransactionRepository;
import dev.ledger.core.inflation.PriceIndex;
import dev.ledger.core.inflation.RealAmount;
import dev.ledger.core.money.Money;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Monthly spending, nominal and in real terms, and the year-on-year comparison the application
 * exists to answer.
 *
 * <p>SPEC §6.5. Everything is reported in Turkish lira (§4.3): a foreign purchase is stored with
 * both amounts, but it settled in lira and that is what was spent.
 */
@Service
public class ReportService {

  /** SPEC §4.3: statements settle in lira, so reports are in lira. */
  private static final java.util.Currency REPORTING_CURRENCY = Money.TRY;

  private final TransactionRepository transactions;
  private final CpiService cpi;

  public ReportService(TransactionRepository transactions, CpiService cpi) {
    this.transactions = transactions;
    this.cpi = cpi;
  }

  /**
   * Spending for one month, broken down by category and subcategory, each figure nominal and real.
   *
   * @param baseMonth the month to express real figures in; when null, the most recent month with a
   *     published CPI (SPEC §6.4)
   */
  @Transactional(readOnly = true)
  public MonthlyReport monthly(YearMonth month, YearMonth baseMonth) {
    PriceIndex index = cpi.index();
    YearMonth base = resolveBase(baseMonth, index);
    Map<String, Map<String, Money>> byCategory = spendByCategory(month);

    List<CategoryLine> categories = new ArrayList<>();
    Money total = Money.zero(REPORTING_CURRENCY);
    for (Map.Entry<String, Map<String, Money>> entry : byCategory.entrySet()) {
      Money categoryTotal = Money.zero(REPORTING_CURRENCY);
      List<SubcategoryLine> subcategories = new ArrayList<>();
      for (Map.Entry<String, Money> sub : entry.getValue().entrySet()) {
        categoryTotal = categoryTotal.plus(sub.getValue());
        subcategories.add(
            new SubcategoryLine(sub.getKey(), RealAmount.of(index, sub.getValue(), month, base)));
      }
      subcategories.sort(
          Comparator.comparing((SubcategoryLine line) -> line.amount().nominal().amount())
              .reversed());
      total = total.plus(categoryTotal);
      categories.add(
          new CategoryLine(
              entry.getKey(), RealAmount.of(index, categoryTotal, month, base), subcategories));
    }
    categories.sort(
        Comparator.comparing((CategoryLine line) -> line.amount().nominal().amount()).reversed());

    return new MonthlyReport(
        month, base, index.covers(month), RealAmount.of(index, total, month, base), categories);
  }

  /**
   * The same month, this year against last, in real terms.
   *
   * <p>SPEC §6.5 calls this the headline feature, and real terms are the whole point of it: in an
   * economy running at thirty per cent, a nominal increase of twenty per cent is a cut. Both months
   * are deflated to the same base, which is what makes the two figures comparable at all.
   *
   * <p>When either month has no published CPI the comparison is reported unadjusted and says so,
   * rather than being quietly presented as if it meant something.
   */
  @Transactional(readOnly = true)
  public YearOverYearReport yearOverYear(YearMonth month, YearMonth baseMonth) {
    PriceIndex index = cpi.index();
    YearMonth base = resolveBase(baseMonth, index);
    YearMonth previous = month.minusYears(1);

    RealAmount thisYear = RealAmount.of(index, totalFor(month), month, base);
    RealAmount lastYear = RealAmount.of(index, totalFor(previous), previous, base);
    boolean comparable = thisYear.adjusted() && lastYear.adjusted();

    Money change = thisYear.real().minus(lastYear.real());
    BigDecimal changePercent =
        lastYear.real().isZero()
            ? null
            : change
                .amount()
                .multiply(BigDecimal.valueOf(100))
                .divide(lastYear.real().amount().abs(), 2, RoundingMode.HALF_UP);

    List<YearOverYearLine> categories = new ArrayList<>();
    Map<String, Money> now = flatten(spendByCategory(month));
    Map<String, Money> before = flatten(spendByCategory(previous));
    List<String> allCategories =
        java.util.stream.Stream.concat(now.keySet().stream(), before.keySet().stream())
            .distinct()
            .sorted()
            .toList();
    for (String category : allCategories) {
      RealAmount a =
          RealAmount.of(
              index, now.getOrDefault(category, Money.zero(REPORTING_CURRENCY)), month, base);
      RealAmount b =
          RealAmount.of(
              index, before.getOrDefault(category, Money.zero(REPORTING_CURRENCY)), previous, base);
      categories.add(new YearOverYearLine(category, a, b, a.real().minus(b.real())));
    }
    categories.sort(
        Comparator.comparing((YearOverYearLine line) -> line.realChange().amount()).reversed());

    return new YearOverYearReport(
        month, previous, base, comparable, thisYear, lastYear, change, changePercent, categories);
  }

  /** Nominal spending in one category in one month, which is what a budget is measured against. */
  @Transactional(readOnly = true)
  public Money nominalSpend(YearMonth month, String categoryId) {
    return spendByCategory(month).getOrDefault(categoryId, Map.of()).values().stream()
        .reduce(Money.zero(REPORTING_CURRENCY), Money::plus);
  }

  /** The months that hold at least one transaction, newest first. */
  @Transactional(readOnly = true)
  public List<YearMonth> monthsWithSpending() {
    return transactions.distinctTransactionDates(CurrentUser.ID).stream()
        .map(YearMonth::from)
        .distinct()
        .sorted(Comparator.reverseOrder())
        .toList();
  }

  private YearMonth resolveBase(YearMonth requested, PriceIndex index) {
    if (requested != null) {
      return requested;
    }
    return cpi.latestPublishedMonth().or(index::latestMonth).orElse(YearMonth.now());
  }

  private Money totalFor(YearMonth month) {
    return flatten(spendByCategory(month)).values().stream()
        .reduce(Money.zero(REPORTING_CURRENCY), Money::plus);
  }

  private static Map<String, Money> flatten(Map<String, Map<String, Money>> byCategory) {
    Map<String, Money> totals = new LinkedHashMap<>();
    byCategory.forEach(
        (category, subs) ->
            totals.put(
                category,
                subs.values().stream().reduce(Money.zero(REPORTING_CURRENCY), Money::plus)));
    return totals;
  }

  /** category -> subcategory -> total, for one month, in the reporting currency only. */
  private Map<String, Map<String, Money>> spendByCategory(YearMonth month) {
    Map<String, Map<String, Money>> byCategory = new LinkedHashMap<>();
    for (Object[] row :
        transactions.sumByCategory(CurrentUser.ID, month.atDay(1), month.atEndOfMonth())) {
      String currency = (String) row[2];
      if (!REPORTING_CURRENCY.getCurrencyCode().equals(currency.trim())) {
        // Nothing the importers produce settles in anything but lira; a CSV could, and it is
        // excluded rather than added to a lira total as if the two were the same money.
        continue;
      }
      String category =
          Optional.ofNullable((String) row[0]).orElse(CategorisationService.UNCATEGORISED_CATEGORY);
      String subcategory =
          Optional.ofNullable((String) row[1])
              .orElse(CategorisationService.UNCATEGORISED_SUBCATEGORY);
      Money amount = Money.of((BigDecimal) row[3], REPORTING_CURRENCY);
      byCategory
          .computeIfAbsent(category, key -> new LinkedHashMap<>())
          .merge(subcategory, amount, Money::plus);
    }
    return byCategory;
  }

  /**
   * @param inflationAdjusted false when {@code month} has no published CPI, which is always true of
   *     the current month. The UI must label the figures rather than present them as real.
   * @param baseMonth surfaced here, not buried: "₺6,200 in today's money" is meaningless without
   *     saying which month is today (SPEC §6.4).
   */
  public record MonthlyReport(
      YearMonth month,
      YearMonth baseMonth,
      boolean inflationAdjusted,
      RealAmount total,
      List<CategoryLine> categories) {}

  public record CategoryLine(
      String categoryId, RealAmount amount, List<SubcategoryLine> subcategories) {}

  public record SubcategoryLine(String subcategoryId, RealAmount amount) {}

  /**
   * @param comparable both months had a published CPI, so the two real figures mean the same thing
   */
  public record YearOverYearReport(
      YearMonth month,
      YearMonth comparedWith,
      YearMonth baseMonth,
      boolean comparable,
      RealAmount thisYear,
      RealAmount lastYear,
      Money realChange,
      BigDecimal realChangePercent,
      List<YearOverYearLine> categories) {}

  public record YearOverYearLine(
      String categoryId, RealAmount thisYear, RealAmount lastYear, Money realChange) {}
}
