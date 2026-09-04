package dev.ledger.core.inflation;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ledger.core.money.Money;
import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** SPEC §6.4. Real values below use the published TÜİK index (2003=100). */
class PriceIndexTest {

  /** Published levels, general index, 2003=100. */
  private static PriceIndex index() {
    Map<YearMonth, BigDecimal> levels = new LinkedHashMap<>();
    levels.put(YearMonth.of(2024, 1), new BigDecimal("1984.02"));
    levels.put(YearMonth.of(2024, 8), new BigDecimal("2453.34"));
    levels.put(YearMonth.of(2025, 1), new BigDecimal("2819.65"));
    levels.put(YearMonth.of(2025, 8), new BigDecimal("3261.72"));
    levels.put(YearMonth.of(2026, 1), new BigDecimal("3683.83"));
    return PriceIndex.of(levels);
  }

  @Test
  @DisplayName("deflating to a later base month restates spending upwards, in today's money")
  void deflatesToALaterBase() {
    // 1000 lira spent in January 2024, expressed in January 2026 money:
    // 1000 × (3683.83 / 1984.02) = 1856.75
    Money real =
        index()
            .deflate(Money.tryLira("1000.00"), YearMonth.of(2024, 1), YearMonth.of(2026, 1))
            .orElseThrow();

    assertThat(real).isEqualTo(Money.tryLira("1856.75"));
  }

  @Test
  @DisplayName("deflating to the base month itself is the identity")
  void baseMonthIsIdentity() {
    Money real =
        index()
            .deflate(Money.tryLira("1234.56"), YearMonth.of(2025, 8), YearMonth.of(2025, 8))
            .orElseThrow();

    assertThat(real).isEqualTo(Money.tryLira("1234.56"));
  }

  @ParameterizedTest(name = "{0} spent {1} is {3} in {2} money")
  @CsvSource({
    "1000.00, 2024-01, 2026-01, 1856.75",
    "1000.00, 2025-01, 2026-01, 1306.48",
    "1000.00, 2026-01, 2024-01, 538.58",
    "6200.00, 2025-08, 2026-01, 7002.36",
    "0.00,    2024-01, 2026-01, 0.00"
  })
  @DisplayName("the calculation is nominal × (CPI_base / CPI_month)")
  void deflationTable(String amount, YearMonth month, YearMonth base, String expected) {
    assertThat(index().deflate(Money.tryLira(amount), month, base))
        .contains(Money.tryLira(expected));
  }

  @Test
  @DisplayName("a refund deflates too, and stays negative")
  void deflatesRefunds() {
    assertThat(
            index().deflate(Money.tryLira("-249.90"), YearMonth.of(2024, 1), YearMonth.of(2026, 1)))
        .contains(Money.tryLira("-464.00"));
  }

  @Test
  @DisplayName("the annual rate is not the index; using it would give a different, wrong answer")
  void theRateIsNotTheIndex() {
    // Year-on-year inflation to January 2026 was 30.65%, so "add 30.65%" gives 1306.50 for a
    // 2024 amount — but two years of compounding actually took it to 1856.75. The index level
    // is what carries that, and the rate cannot (SPEC §6.2).
    Money byIndex =
        index()
            .deflate(Money.tryLira("1000.00"), YearMonth.of(2024, 1), YearMonth.of(2026, 1))
            .orElseThrow();
    Money byAnnualRate = Money.tryLira("1000.00").multiply(new BigDecimal("1.3065"));

    assertThat(byIndex).isNotEqualTo(byAnnualRate);
    assertThat(byIndex).isEqualTo(Money.tryLira("1856.75"));
    assertThat(byAnnualRate).isEqualTo(Money.tryLira("1306.50"));
  }

  @Test
  @DisplayName("a month with no published level yields nothing rather than an extrapolation")
  void missingMonthsAreNotInvented() {
    PriceIndex index = index();

    assertThat(index.covers(YearMonth.of(2026, 2))).isFalse();
    assertThat(index.deflate(Money.tryLira("100.00"), YearMonth.of(2026, 2), YearMonth.of(2026, 1)))
        .isEmpty();
    assertThat(index.deflate(Money.tryLira("100.00"), YearMonth.of(2024, 1), YearMonth.of(2026, 2)))
        .isEmpty();
  }

  @Test
  @DisplayName("the default base month is the most recent published one, not last month")
  void latestMonthIsThePublishedOne() {
    assertThat(index().latestMonth()).contains(YearMonth.of(2026, 1));
    assertThat(index().earliestMonth()).contains(YearMonth.of(2024, 1));
    assertThat(PriceIndex.empty().latestMonth()).isEmpty();
  }

  @Test
  @DisplayName("RealAmount degrades to nominal-only and says so, rather than hiding it")
  void realAmountLabelsUnadjustedMonths() {
    PriceIndex index = index();

    RealAmount adjusted =
        RealAmount.of(
            index, Money.tryLira("1000.00"), YearMonth.of(2024, 1), YearMonth.of(2026, 1));
    RealAmount current =
        RealAmount.of(
            index, Money.tryLira("1000.00"), YearMonth.of(2026, 2), YearMonth.of(2026, 1));

    assertThat(adjusted.adjusted()).isTrue();
    assertThat(adjusted.real()).isEqualTo(Money.tryLira("1856.75"));

    assertThat(current.adjusted()).isFalse();
    assertThat(current.real()).isEqualTo(current.nominal());
    assertThat(current.baseMonth()).isEqualTo(YearMonth.of(2026, 1));
  }

  @Test
  @DisplayName("a non-positive index level is rejected; it would make the ratio meaningless")
  void rejectsNonPositiveLevels() {
    org.assertj.core.api.Assertions.assertThatThrownBy(
            () -> PriceIndex.of(Map.of(YearMonth.of(2024, 1), BigDecimal.ZERO)))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
