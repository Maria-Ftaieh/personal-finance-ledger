package dev.ledger.imports.bank;

import dev.ledger.core.model.BankCode;
import dev.ledger.core.model.ParsedStatement;
import dev.ledger.imports.pdf.PdfDocument;

/**
 * A per-bank PDF adapter. SPEC §4.2.
 *
 * <p>Importers are registered in a list and the first whose {@link #supports} returns true wins, so
 * detection order matters and each check must be specific: an issuer string in the page header, not
 * the presence of the word "banka" somewhere in the document.
 */
public interface StatementImporter {

  /** Cheap sniff. Must not parse the whole document. */
  boolean supports(PdfDocument document);

  ParsedStatement parse(PdfDocument document);

  BankCode bank();
}
