package dev.ledger.imports.bank;

import java.io.Serial;

/** The document was recognised as a bank's statement but did not have the expected shape. */
public final class StatementFormatException extends RuntimeException {

  @Serial private static final long serialVersionUID = 1L;

  public StatementFormatException(String message) {
    super(message);
  }
}
