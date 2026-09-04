package dev.ledger.core.model;

import java.time.LocalDate;
import java.util.Objects;

/** An inclusive date range: the period a statement covers. */
public record DateRange(LocalDate start, LocalDate end) {

  public DateRange {
    Objects.requireNonNull(start, "start");
    Objects.requireNonNull(end, "end");
    if (end.isBefore(start)) {
      throw new IllegalArgumentException(
          "range ends (" + end + ") before it starts (" + start + ")");
    }
  }

  public boolean contains(LocalDate date) {
    return !date.isBefore(start) && !date.isAfter(end);
  }

  public boolean overlaps(DateRange other) {
    return !start.isAfter(other.end) && !other.start.isAfter(end);
  }

  /** The shared days, or empty when the ranges do not overlap. */
  public java.util.Optional<DateRange> intersection(DateRange other) {
    if (!overlaps(other)) {
      return java.util.Optional.empty();
    }
    LocalDate from = start.isAfter(other.start) ? start : other.start;
    LocalDate to = end.isBefore(other.end) ? end : other.end;
    return java.util.Optional.of(new DateRange(from, to));
  }

  /** Derives the covered period from the transactions themselves, when the header does not say. */
  public static DateRange spanning(Iterable<LocalDate> dates) {
    LocalDate min = null;
    LocalDate max = null;
    for (LocalDate date : dates) {
      if (min == null || date.isBefore(min)) {
        min = date;
      }
      if (max == null || date.isAfter(max)) {
        max = date;
      }
    }
    if (min == null) {
      throw new IllegalArgumentException("cannot span an empty set of dates");
    }
    return new DateRange(min, max);
  }
}
