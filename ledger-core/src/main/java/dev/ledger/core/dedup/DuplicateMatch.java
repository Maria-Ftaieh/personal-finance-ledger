package dev.ledger.core.dedup;

import dev.ledger.core.model.StatementId;
import dev.ledger.core.model.TransactionId;

/**
 * A suspected duplicate and the earlier transaction it appears to repeat.
 *
 * <p>SPEC §3.4: nothing is deleted. This record is the evidence shown in the review queue, so it
 * carries the score and the reason and not just the verdict — a user asked to confirm a merge
 * deserves to see why the machine thinks the two lines are one purchase.
 */
public record DuplicateMatch(
    TransactionId duplicate,
    StatementId duplicateStatement,
    TransactionId original,
    StatementId originalStatement,
    double similarity,
    MatchReason reason) {}
