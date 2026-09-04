package dev.ledger.core.money;

import java.io.Serial;
import java.util.Currency;

/**
 * Thrown when arithmetic is attempted between two {@link Money} values in different currencies.
 *
 * <p>SPEC §3.1: mixing currencies must throw rather than silently coerce. A silent coercion in a
 * finance application is a wrong number that nobody notices.
 */
public final class CurrencyMismatchException extends RuntimeException {

  @Serial private static final long serialVersionUID = 1L;

  public CurrencyMismatchException(Currency left, Currency right) {
    super(
        "Cannot combine amounts in different currencies: "
            + left.getCurrencyCode()
            + " and "
            + right.getCurrencyCode());
  }
}
