package dev.ledger.core.rules;

import java.util.Objects;

/** A second-level category, e.g. {@code Kahve} under {@code Yemek}. */
public record SubcategoryId(String value) {

  public SubcategoryId {
    Objects.requireNonNull(value, "value");
    if (value.isBlank()) {
      throw new IllegalArgumentException("subcategory id must not be blank");
    }
  }

  public static SubcategoryId of(String value) {
    return new SubcategoryId(value);
  }

  @Override
  public String toString() {
    return value;
  }
}
