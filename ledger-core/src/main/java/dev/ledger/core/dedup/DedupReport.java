package dev.ledger.core.dedup;

import dev.ledger.core.model.TransactionId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** The outcome of a deduplication pass: suspicions, never deletions. */
public record DedupReport(List<DuplicateMatch> matches) {

  public DedupReport {
    matches = List.copyOf(matches);
  }

  public static DedupReport empty() {
    return new DedupReport(List.of());
  }

  /** Transactions to mark {@code suspected_duplicate}. */
  public Set<TransactionId> suspectedDuplicateIds() {
    return matches.stream()
        .map(DuplicateMatch::duplicate)
        .collect(java.util.stream.Collectors.toSet());
  }

  public Map<TransactionId, DuplicateMatch> byDuplicateId() {
    Map<TransactionId, DuplicateMatch> index = new HashMap<>();
    for (DuplicateMatch match : matches) {
      index.put(match.duplicate(), match);
    }
    return Map.copyOf(index);
  }

  public boolean isEmpty() {
    return matches.isEmpty();
  }

  public int size() {
    return matches.size();
  }
}
