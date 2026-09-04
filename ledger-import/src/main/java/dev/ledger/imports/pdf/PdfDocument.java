package dev.ledger.imports.pdf;

import java.util.List;
import java.util.stream.Collectors;

/**
 * A PDF reduced to positioned text.
 *
 * <p>SPEC §4.1. Statements are laid out as tables, and {@code PDFTextStripper.getText()} flattens a
 * table into a stream of words whose column is no longer recoverable — the amount and the
 * instalment count come out adjacent and indistinguishable. Keeping the x coordinate is what makes
 * {@link ColumnLayout} possible.
 */
public record PdfDocument(String fileName, List<TextLine> lines) {

  public PdfDocument {
    lines = List.copyOf(lines);
  }

  public String plainText() {
    return lines.stream().map(TextLine::text).collect(Collectors.joining("\n"));
  }

  /** Lines near the top of the first page, where an issuer name or logo text sits. */
  public List<TextLine> header(float maxY) {
    return lines.stream().filter(l -> l.page() == 1 && l.y() <= maxY).toList();
  }
}
