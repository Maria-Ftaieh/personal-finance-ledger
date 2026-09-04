package dev.ledger.core.parse;

import dev.ledger.core.model.Installment;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Pulls the {@code 3/8} marker out of a statement description. SPEC §4.3. */
public final class Installments {

  /** {@code 3/8} or {@code 03/08}, but not the {@code 01/2026} of an embedded date. */
  private static final Pattern MARKER =
      Pattern.compile("(?<![\\d/])(\\d{1,2})\\s*/\\s*(\\d{1,2})(?![\\d/])");

  private Installments() {}

  public static Optional<Installment> find(String rawDescription) {
    if (rawDescription == null) {
      return Optional.empty();
    }
    Matcher matcher = MARKER.matcher(rawDescription);
    if (!matcher.find()) {
      return Optional.empty();
    }
    int current = Integer.parseInt(matcher.group(1));
    int total = Integer.parseInt(matcher.group(2));
    if (total < 1 || current < 1 || current > total) {
      return Optional.empty();
    }
    return Optional.of(new Installment(current, total));
  }
}
