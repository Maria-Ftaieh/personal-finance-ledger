package dev.ledger.core.rules;

import java.util.Objects;
import java.util.UUID;

/** Identity of a categorisation rule. Also the deterministic tiebreak when priorities collide. */
public record RuleId(UUID value) implements Comparable<RuleId> {

  public RuleId {
    Objects.requireNonNull(value, "value");
  }

  public static RuleId newId() {
    return new RuleId(UUID.randomUUID());
  }

  public static RuleId of(String uuid) {
    return new RuleId(UUID.fromString(uuid));
  }

  @Override
  public int compareTo(RuleId other) {
    return value.compareTo(other.value);
  }

  @Override
  public String toString() {
    return value.toString();
  }
}
