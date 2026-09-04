package dev.ledger.core.model;

import java.util.Objects;
import java.util.UUID;

/** Identity of a single transaction row. A wrapper, so it cannot be swapped with a StatementId. */
public record TransactionId(UUID value) {

  public TransactionId {
    Objects.requireNonNull(value, "value");
  }

  public static TransactionId newId() {
    return new TransactionId(UUID.randomUUID());
  }

  public static TransactionId of(String uuid) {
    return new TransactionId(UUID.fromString(uuid));
  }

  @Override
  public String toString() {
    return value.toString();
  }
}
