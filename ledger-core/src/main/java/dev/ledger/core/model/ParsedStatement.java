package dev.ledger.core.model;

import java.util.List;
import java.util.Objects;

/**
 * One statement file, parsed.
 *
 * @param contentHash SHA-256 of the source file. SPEC §5.2 uses it to make re-uploading the
 *     identical file a no-op that never reaches the parser. May be {@code null} in tests that
 *     construct a statement directly.
 * @param period the days the statement covers, which is what makes deduplication tractable: only
 *     statements whose periods overlap can contain the same purchase twice.
 */
public record ParsedStatement(
    StatementId id,
    BankCode bank,
    String sourceFileName,
    String contentHash,
    DateRange period,
    List<Transaction> transactions) {

  public ParsedStatement {
    Objects.requireNonNull(id, "id");
    Objects.requireNonNull(bank, "bank");
    Objects.requireNonNull(period, "period");
    transactions = List.copyOf(Objects.requireNonNull(transactions, "transactions"));
  }

  public int size() {
    return transactions.size();
  }
}
