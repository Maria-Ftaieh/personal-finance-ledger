package dev.ledger.imports.pdf;

import dev.ledger.core.text.TurkishText;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;

/**
 * Extracts text with coordinates and groups it into lines.
 *
 * <p>Text is NFC normalised here, at the extraction boundary, so nothing downstream has to care
 * whether the PDF's font encoding produced a composed {@code ş} or an {@code s} with a combining
 * cedilla (SPEC §3.2).
 */
public final class PositionalTextExtractor extends PDFTextStripper {

  /** Baselines within this many points of each other belong to the same row. */
  private static final float LINE_TOLERANCE = 2.0f;

  private final List<TextChunk> chunks = new ArrayList<>();
  private final List<Integer> chunkPages = new ArrayList<>();

  private PositionalTextExtractor() throws IOException {
    setSortByPosition(true);
  }

  public static PdfDocument extract(PDDocument document, String fileName) throws IOException {
    PositionalTextExtractor extractor = new PositionalTextExtractor();
    extractor.getText(document);
    return new PdfDocument(fileName, extractor.toLines());
  }

  @Override
  protected void writeString(String text, List<TextPosition> textPositions) throws IOException {
    if (textPositions.isEmpty() || text.isBlank()) {
      return;
    }
    TextPosition first = textPositions.get(0);
    TextPosition last = textPositions.get(textPositions.size() - 1);
    chunks.add(
        new TextChunk(
            TurkishText.normalise(text),
            first.getXDirAdj(),
            last.getXDirAdj() + last.getWidthDirAdj(),
            first.getYDirAdj()));
    chunkPages.add(getCurrentPageNo());
    super.writeString(text, textPositions);
  }

  private List<TextLine> toLines() {
    // Page, then baseline rounded to the tolerance: two chunks printed at 412.0 and 412.7 are one
    // row, and grouping on the raw float would split them.
    Map<Integer, Map<Integer, List<TextChunk>>> byPage = new TreeMap<>();
    for (int i = 0; i < chunks.size(); i++) {
      TextChunk chunk = chunks.get(i);
      byPage
          .computeIfAbsent(chunkPages.get(i), p -> new TreeMap<>())
          .computeIfAbsent(Math.round(chunk.y() / LINE_TOLERANCE), y -> new ArrayList<>())
          .add(chunk);
    }

    List<TextLine> lines = new ArrayList<>();
    byPage.forEach(
        (page, rows) ->
            rows.forEach(
                (band, row) -> {
                  row.sort(Comparator.comparing(TextChunk::startX));
                  lines.add(new TextLine(page, row.get(0).y(), row));
                }));
    return lines;
  }
}
