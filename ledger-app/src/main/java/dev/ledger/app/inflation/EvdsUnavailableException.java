package dev.ledger.app.inflation;

import java.io.Serial;

/**
 * EVDS could not be reached or did not answer usefully.
 *
 * <p>Never fatal. SPEC §6.3: the application serves real-spending figures from its own cache, and a
 * TCMB outage must leave it working rather than failing a report.
 */
public class EvdsUnavailableException extends RuntimeException {

  @Serial private static final long serialVersionUID = 1L;

  public EvdsUnavailableException(String message) {
    super(message);
  }

  public EvdsUnavailableException(String message, Throwable cause) {
    super(message, cause);
  }
}
