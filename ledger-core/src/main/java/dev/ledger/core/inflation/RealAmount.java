package dev.ledger.core.inflation;

import dev.ledger.core.money.Money;
import java.time.YearMonth;

/**
 * A figure in both nominal and real terms, carrying whether the adjustment could actually be made.
 *
 * <p>SPEC §6.2 and §6.4. The {@code adjusted} flag is the point of this type. When a month has no
 * published CPI — always true of the current month — {@code real} is the nominal figure and this
 * says so, so the UI can label it rather than quietly presenting an unadjusted number as if it were
 * comparable. Extrapolating a missing month would remove the caller's ability to tell.
 *
 * @param baseMonth the month whose money {@code real} is expressed in. "₺6,200 in today's money"
 *     means nothing without it, which is why it travels with the number rather than sitting in a
 *     separate field somewhere up the response.
 */
public record RealAmount(Money nominal, Money real, YearMonth baseMonth, boolean adjusted) {

  public static RealAmount adjusted(Money nominal, Money real, YearMonth baseMonth) {
    return new RealAmount(nominal, real, baseMonth, true);
  }

  /**
   * The month has no published index yet: report the nominal figure, and say that is what it is.
   */
  public static RealAmount nominalOnly(Money nominal, YearMonth baseMonth) {
    return new RealAmount(nominal, nominal, baseMonth, false);
  }

  /** Applies the index if it can, and degrades to nominal-only if it cannot. */
  public static RealAmount of(
      PriceIndex index, Money nominal, YearMonth month, YearMonth baseMonth) {
    return index
        .deflate(nominal, month, baseMonth)
        .map(real -> adjusted(nominal, real, baseMonth))
        .orElseGet(() -> nominalOnly(nominal, baseMonth));
  }
}
