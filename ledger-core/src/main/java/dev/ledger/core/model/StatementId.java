package dev.ledger.core.model;

import java.util.Objects;
import java.util.UUID;

/** Identity of one imported statement file. */
public record StatementId(UUID value) {

  public StatementId {
    Objects.requireNonNull(value, "value");
  }

  public static StatementId newId() {
    return new StatementId(UUID.randomUUID());
  }

  public static StatementId of(String uuid) {
    return new StatementId(UUID.fromString(uuid));
  }

  @Override
  public String toString() {
    return value.toString();
  }
}
