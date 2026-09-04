package dev.ledger.imports.pdf;

import java.util.List;
import java.util.stream.Collectors;

/** All chunks sharing a baseline, left to right. */
public record TextLine(int page, float y, List<TextChunk> chunks) {

  public TextLine {
    chunks = List.copyOf(chunks);
  }

  public String text() {
    return chunks.stream().map(TextChunk::text).collect(Collectors.joining(" "));
  }
}
