package dev.ledger.core.money;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.util.Currency;
import java.util.Locale;
import java.util.Objects;

/**
 * A monetary amount: an exact decimal paired with the currency it is denominated in.
 *
 * <p>SPEC §3.1. Money is never a {@code double}. Binary floating point cannot represent 0.01, so
 * every addition of a price accumulates error; {@link dev.ledger.core.money.MoneyPrecisionTest}
 * demonstrates the failure directly.
 *
 * <p><b>Scale.</b> Amounts are held at a fixed scale of {@value #SCALE}, which is the minor-unit
 * count of every currency this application handles (TRY, EUR, USD). Fixing the scale in the
 * canonical constructor means {@code 1.5} and {@code 1.50} are the same value, so the record's
 * generated {@code equals} — which delegates to {@link BigDecimal#equals} and is therefore scale
 * sensitive — behaves the way a reader expects.
 *
 * <p><b>Rounding.</b> {@link RoundingMode#HALF_UP} is used throughout. This is the rule a Turkish
 * bank statement is rounded with and the rule a human doing the sum by hand would apply, so a
 * reported total matches what the user can check on paper. HALF_EVEN would spread rounding error
 * more evenly across a large population of transactions, but it produces figures that disagree with
 * the statement the user is holding, and agreeing with the statement matters more here.
 */
public record Money(BigDecimal amount, Currency currency) implements Comparable<Money> {

  /** Minor units held for every supported currency. */
  public static final int SCALE = 2;

  /** See the class comment for why HALF_UP rather than HALF_EVEN. */
  public static final RoundingMode ROUNDING = RoundingMode.HALF_UP;

  public static final Currency TRY = Currency.getInstance("TRY");
  public static final Currency EUR = Currency.getInstance("EUR");
  public static final Currency USD = Currency.getInstance("USD");

  public Money {
    Objects.requireNonNull(amount, "amount");
    Objects.requireNonNull(currency, "currency");
    amount = amount.setScale(SCALE, ROUNDING);
  }

  public static Money of(BigDecimal amount, Currency currency) {
    return new Money(amount, currency);
  }

  /**
   * Builds an amount from its decimal text. The string must use {@code .} as the decimal separator
   * — this is the machine representation, not user input. Statement text is parsed by {@link
   * dev.ledger.core.parse.TurkishNumbers} instead.
   */
  public static Money of(String amount, Currency currency) {
    return new Money(new BigDecimal(amount), currency);
  }

  public static Money of(long units, Currency currency) {
    return new Money(BigDecimal.valueOf(units), currency);
  }

  public static Money tryLira(String amount) {
    return of(amount, TRY);
  }

  public static Money zero(Currency currency) {
    return new Money(BigDecimal.ZERO, currency);
  }

  public Money plus(Money other) {
    requireSameCurrency(other);
    return new Money(amount.add(other.amount), currency);
  }

  public Money minus(Money other) {
    requireSameCurrency(other);
    return new Money(amount.subtract(other.amount), currency);
  }

  public Money negate() {
    return new Money(amount.negate(), currency);
  }

  public Money abs() {
    return new Money(amount.abs(), currency);
  }

  /** Scales the amount, rounding the result back to {@link #SCALE} with {@link #ROUNDING}. */
  public Money multiply(BigDecimal factor) {
    return new Money(amount.multiply(factor), currency);
  }

  /**
   * Divides by a factor, keeping extra intermediate precision before rounding back to {@link
   * #SCALE}. Used by inflation deflation, where the ratio itself carries several decimal places.
   */
  public Money divide(BigDecimal divisor) {
    return new Money(amount.divide(divisor, SCALE + 4, ROUNDING), currency);
  }

  public boolean isNegative() {
    return amount.signum() < 0;
  }

  public boolean isPositive() {
    return amount.signum() > 0;
  }

  public boolean isZero() {
    return amount.signum() == 0;
  }

  public boolean sameCurrencyAs(Money other) {
    return currency.equals(other.currency);
  }

  private void requireSameCurrency(Money other) {
    if (!sameCurrencyAs(other)) {
      throw new CurrencyMismatchException(currency, other.currency);
    }
  }

  /** Only comparable within one currency; comparing across currencies throws. */
  @Override
  public int compareTo(Money other) {
    requireSameCurrency(other);
    return amount.compareTo(other.amount);
  }

  /**
   * The wire and log representation: {@code "1234.56"}. SPEC §3.1 requires money to cross the JSON
   * boundary as a string, because a JSON number becomes a JavaScript double on the frontend and
   * loses precision.
   */
  @Override
  public String toString() {
    return amount.toPlainString();
  }

  /** Localised presentation, e.g. {@code ₺1.234,56} for {@code tr-TR}. Never used for matching. */
  public String format(Locale locale) {
    NumberFormat formatter = NumberFormat.getCurrencyInstance(locale);
    formatter.setCurrency(currency);
    return formatter.format(amount);
  }
}
