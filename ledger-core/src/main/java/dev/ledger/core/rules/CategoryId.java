package dev.ledger.core.rules;

import java.util.Objects;

/** A top-level spending category. The seed set is Turkish (SPEC §5.3) and lives in Flyway. */
public record CategoryId(String value) {

  public CategoryId {
    Objects.requireNonNull(value, "value");
    if (value.isBlank()) {
      throw new IllegalArgumentException("category id must not be blank");
    }
  }

  public static CategoryId of(String value) {
    return new CategoryId(value);
  }

  @Override
  public String toString() {
    return value;
  }
}
