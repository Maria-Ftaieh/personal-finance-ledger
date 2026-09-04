package dev.ledger.core.model;

/**
 * Every way importing a file can end.
 *
 * <p>SPEC §3.3. Sealed so that a caller switching over the outcome must handle all four cases; a
 * new outcome added here becomes a compile error at every call site rather than a silently ignored
 * branch.
 */
public sealed interface ImportResult {

  /** The file was read and parsed. */
  record Parsed(ParsedStatement statement) implements ImportResult {}

  /**
   * The PDF is encrypted and the supplied password (if any) did not open it, so the API should
   * prompt for one.
   *
   * <p>This carries no password field on purpose. SPEC §4.3: the password must never be logged, and
   * the surest way to keep it out of a log is to keep it out of the objects that get logged.
   */
  record NeedsPassword(String fileName) implements ImportResult {}

  /** The file was readable but no registered adapter recognised the issuer. */
  record UnsupportedBank(String fileName, String detail) implements ImportResult {}

  /**
   * The file yielded too little text to be a statement — typically a scan. SPEC §4.3 puts OCR out
   * of scope: returning this is better than emitting plausible-looking garbage rows.
   */
  record Unreadable(String fileName, String reason) implements ImportResult {}
}
