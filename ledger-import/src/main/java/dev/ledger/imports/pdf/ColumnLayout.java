package dev.ledger.imports.pdf;

import dev.ledger.core.text.TurkishText;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Column boundaries derived from a statement's header row.
 *
 * <p>A column starts where its header label starts and runs until the next label begins, and a
 * chunk belongs to the last column that starts at or before it. Anchoring on the left edge rather
 * than on the midpoint between labels is what makes a wide column with a narrow header work: a
 * description column can be 200 points wide under the word "Açıklama", and a midpoint rule would
 * push its later words into the next column.
 *
 * <p>The assumption this rests on is that a table's columns are left-bounded and separated by a
 * visible gutter, which is true of every statement layout in the corpus. The one thing it needs
 * from the document is that a right-aligned numeric column's values do not start to the left of
 * their own header, so a small tolerance is allowed for the rounding in the two coordinates.
 */
public final class ColumnLayout {

  /** Slack, in points, for the rounding between a header's x and its column's data. */
  private static final float TOLERANCE = 3f;

  private final Map<String, Band> bands;

  private ColumnLayout(Map<String, Band> bands) {
    this.bands = bands;
  }

  private record Band(float start, float end) {}

  private record Anchor(float startX, float endX, int nextChunk) {}

  /**
   * Locates each label in {@code header}, left to right, and builds the bands.
   *
   * @param labels header texts in order, matched ASCII-folded and case insensitively
   * @return empty if any label is missing — which is how an importer rejects a page it cannot read
   */
  public static Optional<ColumnLayout> fromHeader(TextLine header, List<String> labels) {
    List<Anchor> anchors = new ArrayList<>();
    int cursor = 0;
    for (String label : labels) {
      Optional<Anchor> anchor = findLabel(header.chunks(), cursor, label);
      if (anchor.isEmpty()) {
        return Optional.empty();
      }
      anchors.add(anchor.get());
      cursor = anchor.get().nextChunk();
    }

    Map<String, Band> bands = new LinkedHashMap<>();
    for (int i = 0; i < labels.size(); i++) {
      float start = i == 0 ? Float.NEGATIVE_INFINITY : anchors.get(i).startX() - TOLERANCE;
      float end =
          i == labels.size() - 1
              ? Float.POSITIVE_INFINITY
              : anchors.get(i + 1).startX() - TOLERANCE;
      bands.put(labels.get(i), new Band(start, end));
    }
    return Optional.of(new ColumnLayout(bands));
  }

  /** The text of one cell on a data row; empty when the row has nothing in that column. */
  public String cell(TextLine line, String label) {
    Band band = bands.get(label);
    if (band == null) {
      throw new IllegalArgumentException("no such column: " + label);
    }
    return line.chunks().stream()
        .filter(c -> c.startX() >= band.start() && c.startX() < band.end())
        .map(TextChunk::text)
        .collect(Collectors.joining(" "))
        .trim();
  }

  /**
   * Finds a label that may have been split across chunks — the extractor emits one chunk per word,
   * so "İşlem Tarihi" arrives as two. Consecutive chunks are accumulated while they remain a prefix
   * of the wanted label; a chunk that carries more than the label — a header printed as "Tutar
   * (TL)" against the label "Tutar" — also matches. Comparison is on the ASCII-folded,
   * punctuation-free form.
   */
  private static Optional<Anchor> findLabel(List<TextChunk> chunks, int fromIndex, String label) {
    String wanted = fold(label);
    for (int i = fromIndex; i < chunks.size(); i++) {
      StringBuilder accumulated = new StringBuilder();
      for (int j = i; j < chunks.size(); j++) {
        accumulated.append(fold(chunks.get(j).text()));
        String sofar = accumulated.toString();
        if (sofar.isEmpty()) {
          break;
        }
        if (sofar.startsWith(wanted)) {
          return Optional.of(new Anchor(chunks.get(i).startX(), chunks.get(j).endX(), j + 1));
        }
        if (!wanted.startsWith(sofar)) {
          break;
        }
      }
    }
    return Optional.empty();
  }

  private static String fold(String text) {
    return TurkishText.upperForMatching(TurkishText.foldToAscii(text)).replaceAll("[^A-Z0-9]", "");
  }
}
