package dev.ledger.core.model;

/**
 * An instalment marker such as {@code 3/8} — the third of eight monthly charges.
 *
 * <p>SPEC §4.3: the amount printed on the row is the instalment charged this month, not the
 * purchase total. Storing the structure keeps that distinction available to the report.
 */
public record Installment(int current, int total) {

  public Installment {
    if (total < 1) {
      throw new IllegalArgumentException("instalment total must be at least 1, was " + total);
    }
    if (current < 1 || current > total) {
      throw new IllegalArgumentException("instalment " + current + " is outside 1.." + total);
    }
  }

  public boolean isFirst() {
    return current == 1;
  }

  public boolean isLast() {
    return current == total;
  }

  @Override
  public String toString() {
    return current + "/" + total;
  }
}
