package dev.ledger.core.inflation;

import dev.ledger.core.money.Money;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.YearMonth;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Optional;
import java.util.TreeMap;

/**
 * A consumer price index: the published level for each month, and the arithmetic that turns nominal
 * spending into real spending.
 *
 * <p>SPEC §6.2 and §6.4. What matters here is that this holds the <b>index level</b> and not the
 * annual inflation rate. They are different numbers and deflating with the rate gives a wrong
 * answer: the rate tells you how much prices rose over twelve months, while comparing March 2024
 * with March 2026 needs the ratio of the two months' levels, which no single annual figure
 * contains.
 *
 * <pre>{@code real = nominal × (CPI_base / CPI_transactionMonth)}</pre>
 *
 * <p>Immutable, and free of any notion of where the numbers came from — the seeded CSV and the EVDS
 * feed produce the same object.
 */
public final class PriceIndex {

  /**
   * Digits kept in the deflation ratio before the result is rounded back to a money scale. The
   * ratio between two months two decades apart is around 45, and between adjacent months around
   * 1.02; ten digits keeps the second case exact enough that rounding is decided by the amount and
   * not by the ratio.
   */
  static final int RATIO_SCALE = 10;

  private final NavigableMap<YearMonth, BigDecimal> levels;

  private PriceIndex(NavigableMap<YearMonth, BigDecimal> levels) {
    this.levels = levels;
  }

  public static PriceIndex of(Map<YearMonth, BigDecimal> levels) {
    NavigableMap<YearMonth, BigDecimal> copy = new TreeMap<>();
    levels.forEach(
        (month, level) -> {
          if (level.signum() <= 0) {
            throw new IllegalArgumentException(
                "index level for " + month + " is not positive: " + level);
          }
          copy.put(month, level);
        });
    return new PriceIndex(copy);
  }

  public static PriceIndex empty() {
    return new PriceIndex(new TreeMap<>());
  }

  public Optional<BigDecimal> at(YearMonth month) {
    return Optional.ofNullable(levels.get(month));
  }

  /**
   * The most recent month with a published level.
   *
   * <p>SPEC §6.4 makes this the default base month, and §6.2 explains why it is never simply "last
   * month": CPI is published in the first days of the following month, so the current month is
   * always missing, and a release can be later than that.
   */
  public Optional<YearMonth> latestMonth() {
    return levels.isEmpty() ? Optional.empty() : Optional.of(levels.lastKey());
  }

  public Optional<YearMonth> earliestMonth() {
    return levels.isEmpty() ? Optional.empty() : Optional.of(levels.firstKey());
  }

  public boolean covers(YearMonth month) {
    return levels.containsKey(month);
  }

  public int size() {
    return levels.size();
  }

  public NavigableMap<YearMonth, BigDecimal> levels() {
    return java.util.Collections.unmodifiableNavigableMap(levels);
  }

  /**
   * Restates an amount spent in {@code month} in the money of {@code baseMonth}.
   *
   * <p>Returns empty when either month has no published level. That is a real and frequent case —
   * the current month always lacks one — and it is deliberately not an exception and never an
   * extrapolation: SPEC §6.2 requires the month to be reported as nominal and labelled as not yet
   * adjusted, which the caller can only do if it is told.
   */
  public Optional<Money> deflate(Money nominal, YearMonth month, YearMonth baseMonth) {
    return ratio(month, baseMonth).map(nominal::multiply);
  }

  /** {@code CPI_base / CPI_month}: greater than 1 when the base month is later. */
  public Optional<BigDecimal> ratio(YearMonth month, YearMonth baseMonth) {
    Optional<BigDecimal> from = at(month);
    Optional<BigDecimal> to = at(baseMonth);
    if (from.isEmpty() || to.isEmpty()) {
      return Optional.empty();
    }
    return Optional.of(to.get().divide(from.get(), RATIO_SCALE, RoundingMode.HALF_UP));
  }
}
