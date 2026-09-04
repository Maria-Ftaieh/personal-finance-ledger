package dev.ledger.core.model;

/**
 * The banks this build can import. SPEC §4.2 asks for two real adapters plus a CSV fallback —
 * enough to prove the abstraction without turning the project into a collection of parsers.
 */
public enum BankCode {
  GARANTI_BBVA("Garanti BBVA"),
  YAPI_KREDI("Yapı Kredi"),
  /** Anything imported through the CSV/XLS export path rather than a PDF. */
  GENERIC_CSV("Generic CSV");

  private final String displayName;

  BankCode(String displayName) {
    this.displayName = displayName;
  }

  /** Presentation only. UI strings go through i18n; this is the bank's own name, not a label. */
  public String displayName() {
    return displayName;
  }
}
