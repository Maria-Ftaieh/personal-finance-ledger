package dev.ledger.imports.pdf;

/** A run of text with the horizontal band it occupies on the page. */
public record TextChunk(String text, float startX, float endX, float y) {

  public float centreX() {
    return (startX + endX) / 2;
  }
}
