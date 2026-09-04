package dev.ledger.core.dedup;

/** Why two lines were judged to be the same purchase. Shown in the review queue. */
public enum MatchReason {
  /** Token-set Jaccard cleared the threshold. */
  DESCRIPTION_SIMILARITY,
  /** One description is the other cut off at a narrower column width. */
  TRUNCATED_DESCRIPTION
}
