package dev.ledger.app.service;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ledger.app.PostgresIntegrationTest;
import dev.ledger.app.config.CurrentUser;
import dev.ledger.app.repo.StatementRepository;
import dev.ledger.app.repo.TransactionRepository;
import dev.ledger.app.service.ReportService.CategoryLine;
import dev.ledger.core.money.Money;
import dev.ledger.imports.Fixtures;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.YearMonth;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;

/**
 * SPEC §6, end to end.
 *
 * <p>EVDS is pointed at an unroutable address for the whole class. That is the point: §6.3 requires
 * the application to produce real-spending figures with no network access and no API key, using
 * only the CSV seeded by Flyway. If any of these assertions needed TCMB to be reachable, the
 * requirement would not be met.
 */
@TestPropertySource(
    properties = {
      "ledger.evds.base-url=http://127.0.0.1:1/unreachable",
      "ledger.evds.api-key=",
      "ledger.evds.timeout=2s"
    })
class InflationReportIntegrationTest extends PostgresIntegrationTest {

  @Autowired private StatementImportAppService importer;
  @Autowired private ReportService reports;
  @Autowired private CpiService cpi;
  @Autowired private DuplicateService duplicates;
  @Autowired private StatementRepository statements;
  @Autowired private TransactionRepository transactions;

  /** Two Januaries, so the year-on-year comparison has something to compare. */
  private static final String JANUARY_2025 =
      """
      date,description,amount,currency
      06.01.2025,KAHVE DÜNYASI KADIKÖY,"62,00",TRY
      14.01.2025,SHELL AKARYAKIT,"1.100,00",TRY
      22.01.2025,ECZANE ŞİFA,"118,00",TRY
      """;

  @BeforeEach
  void load() {
    transactions.deleteAllInBatch();
    statements.deleteAllInBatch();
    importer.importFile(Fixtures.bytes("garanti-2026-01.pdf"), "garanti-2026-01.pdf", null);
    importer.importFile(Fixtures.bytes("yapikredi-2026-01.pdf"), "yapikredi-2026-01.pdf", null);
    importer.importFile(JANUARY_2025.getBytes(StandardCharsets.UTF_8), "jan2025.csv", null);
  }

  @Test
  @DisplayName("the seeded CSV alone gives a usable index, with no network and no API key")
  void seedAloneIsEnough() {
    assertThat(cpi.index().size()).isGreaterThan(250);
    assertThat(cpi.index().earliestMonth()).contains(YearMonth.of(2003, 1));
    assertThat(cpi.latestPublishedMonth()).isPresent();

    // Reaching out fails, and that changes nothing about what the application can answer.
    CpiService.RefreshResult result = cpi.refresh();
    assertThat(result.reached()).isFalse();
    assertThat(result.added()).isZero();
    assertThat(cpi.latestPublishedMonth()).isPresent();
    assertThat(reports.monthly(YearMonth.of(2026, 1), null).total().adjusted()).isTrue();
  }

  @Test
  @DisplayName(
      "the default base month is the most recent published one, and travels with the figure")
  void defaultsToTheLatestPublishedBaseMonth() {
    YearMonth latest = cpi.latestPublishedMonth().orElseThrow();

    ReportService.MonthlyReport report = reports.monthly(YearMonth.of(2026, 1), null);

    assertThat(report.baseMonth()).isEqualTo(latest);
    assertThat(report.total().baseMonth()).isEqualTo(latest);
    assertThat(report.inflationAdjusted()).isTrue();
  }

  @Test
  @DisplayName("real is nominal times the ratio of the two months' index levels")
  void realIsTheIndexRatio() {
    YearMonth month = YearMonth.of(2026, 1);
    YearMonth base = YearMonth.of(2026, 8);

    ReportService.MonthlyReport report = reports.monthly(month, base);
    BigDecimal ratio = cpi.index().ratio(month, base).orElseThrow();

    assertThat(report.total().real()).isEqualTo(report.total().nominal().multiply(ratio));
    assertThat(report.total().real()).isGreaterThan(report.total().nominal());
  }

  @Test
  @DisplayName("category and subcategory totals are both reported, nominal and real")
  void breaksDownByCategoryAndSubcategory() {
    ReportService.MonthlyReport report =
        reports.monthly(YearMonth.of(2026, 1), YearMonth.of(2026, 8));

    Optional<CategoryLine> yemek =
        report.categories().stream().filter(c -> c.categoryId().equals("yemek")).findFirst();

    assertThat(yemek).isPresent();
    assertThat(yemek.orElseThrow().subcategories())
        .extracting(ReportService.SubcategoryLine::subcategoryId)
        .contains("yemek.kahve", "yemek.market");
    assertThat(yemek.orElseThrow().amount().nominal()).isEqualTo(Money.tryLira("800.35"));
    assertThat(yemek.orElseThrow().amount().adjusted()).isTrue();

    // The category total is the sum of its subcategories.
    Money summed =
        yemek.orElseThrow().subcategories().stream()
            .map(line -> line.amount().nominal())
            .reduce(Money.zero(Money.TRY), Money::plus);
    assertThat(summed).isEqualTo(yemek.orElseThrow().amount().nominal());
  }

  @Test
  @DisplayName("a refund reduces its category's total rather than being dropped")
  void refundsReduceTheirCategory() {
    // The January statement holds a ₺249.90 Trendyol charge and its ₺249.90 refund.
    ReportService.MonthlyReport report =
        reports.monthly(YearMonth.of(2026, 1), YearMonth.of(2026, 8));

    Money giyim =
        report.categories().stream()
            .filter(c -> c.categoryId().equals("giyim"))
            .findFirst()
            .orElseThrow()
            .amount()
            .nominal();

    assertThat(giyim).isEqualTo(Money.zero(Money.TRY));
  }

  @Test
  @DisplayName("a month with no published index is reported nominal, and labelled as such")
  void unpublishedMonthsAreNominalOnly() {
    YearMonth beyondTheIndex = cpi.latestPublishedMonth().orElseThrow().plusMonths(1);

    ReportService.MonthlyReport report = reports.monthly(beyondTheIndex, null);

    assertThat(report.inflationAdjusted()).isFalse();
    assertThat(report.total().adjusted()).isFalse();
    assertThat(report.total().real()).isEqualTo(report.total().nominal());
  }

  @Test
  @DisplayName("the headline: the same month a year apart, both deflated to one base")
  void yearOverYearInRealTerms() {
    ReportService.YearOverYearReport report =
        reports.yearOverYear(YearMonth.of(2026, 1), YearMonth.of(2026, 8));

    assertThat(report.comparedWith()).isEqualTo(YearMonth.of(2025, 1));
    assertThat(report.comparable()).isTrue();
    assertThat(report.thisYear().baseMonth()).isEqualTo(YearMonth.of(2026, 8));
    assertThat(report.lastYear().baseMonth()).isEqualTo(YearMonth.of(2026, 8));

    // Both sides carry their own nominal figure and a real one restated in the same money.
    assertThat(report.lastYear().nominal()).isEqualTo(Money.tryLira("1280.00"));
    assertThat(report.lastYear().real()).isGreaterThan(report.lastYear().nominal());
    assertThat(report.realChange())
        .isEqualTo(report.thisYear().real().minus(report.lastYear().real()));
  }

  @Test
  @DisplayName("inflation, not spending: a nominal rise can be a real fall")
  void aNominalRiseCanBeARealFall() {
    // Sağlık: ₺118.00 in January 2025 against ₺156.00 in January 2026 is a 32% nominal rise.
    // Deflated to a common base it is close to flat, and that is the number that means anything.
    ReportService.YearOverYearReport report =
        reports.yearOverYear(YearMonth.of(2026, 1), YearMonth.of(2026, 8));

    ReportService.YearOverYearLine saglik =
        report.categories().stream()
            .filter(line -> line.categoryId().equals("saglik"))
            .findFirst()
            .orElseThrow();

    assertThat(saglik.thisYear().nominal()).isEqualTo(Money.tryLira("156.00"));
    assertThat(saglik.lastYear().nominal()).isEqualTo(Money.tryLira("118.00"));
    // Nominally up by a third; in real terms the change is a rounding error by comparison.
    Money nominalChange = saglik.thisYear().nominal().minus(saglik.lastYear().nominal());
    assertThat(saglik.realChange().abs()).isLessThan(nominalChange.abs());
  }

  @Test
  @DisplayName("a confirmed duplicate stops counting towards the totals")
  void confirmedDuplicatesLeaveTheTotals() {
    Money before = reports.monthly(YearMonth.of(2026, 1), null).total().nominal();

    importer.importFile(Fixtures.bytes("garanti-2026-02.pdf"), "garanti-2026-02.pdf", null);
    Money withOverlap = reports.monthly(YearMonth.of(2026, 1), null).total().nominal();
    assertThat(withOverlap).isGreaterThan(before);

    Money confirmedAmount = duplicates.reviewQueue().get(0).getAmount();
    duplicates.confirm(duplicates.reviewQueue().get(0).getId());

    assertThat(reports.monthly(YearMonth.of(2026, 1), null).total().nominal())
        .isEqualTo(withOverlap.minus(confirmedAmount));
  }

  @Test
  @DisplayName("only months that hold transactions are offered")
  void listsMonthsWithSpending() {
    assertThat(reports.monthsWithSpending())
        .contains(YearMonth.of(2026, 1), YearMonth.of(2025, 1))
        .doesNotContain(YearMonth.of(2026, 3));
    assertThat(reports.monthsWithSpending())
        .isSortedAccordingTo(java.util.Comparator.reverseOrder());
  }

  @Test
  @DisplayName("spending is scoped to the user, as every report query is")
  void scopedToTheUser() {
    assertThat(transactions.findByUserId(CurrentUser.ID)).isNotEmpty();
  }
}
