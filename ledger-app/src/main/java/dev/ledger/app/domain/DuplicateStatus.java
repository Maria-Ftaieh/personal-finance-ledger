package dev.ledger.app.domain;

/**
 * Where a transaction stands in the duplicate review queue. SPEC §3.4: a suspected duplicate is
 * flagged and reviewed, never deleted.
 */
public enum DuplicateStatus {
  /** Not suspected of duplicating anything. */
  NONE,
  /** The detector matched it to an earlier transaction; awaiting the user's decision. */
  SUSPECTED,
  /** The user agreed it is a duplicate. Excluded from totals; the row stays. */
  CONFIRMED,
  /** The user said it is a genuine separate purchase. Counted, and never re-flagged. */
  REJECTED
}
