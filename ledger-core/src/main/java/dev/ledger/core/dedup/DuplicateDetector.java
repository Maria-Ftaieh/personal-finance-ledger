package dev.ledger.core.dedup;

import dev.ledger.core.model.ParsedStatement;
import dev.ledger.core.model.StatementId;
import dev.ledger.core.model.Transaction;
import dev.ledger.core.model.TransactionId;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Finds the same purchase printed on two different statements.
 *
 * <p>SPEC §3.4. The problem is overlap: consecutive statements share the days between one period's
 * end and the next one's start, so uploading January and February lists those days' purchases
 * twice.
 *
 * <p>The constraint that shapes the whole algorithm is that <b>two identical purchases on the same
 * day are legitimate</b>. Two coffees from the same shop for the same price on the same morning is
 * an ordinary Tuesday, and a naive unique constraint on (date, amount, description) would erase one
 * of them permanently. So:
 *
 * <ul>
 *   <li>Deduplication only ever runs <em>between</em> statements, never within one. Repetition
 *       inside a single document is data, not noise.
 *   <li>Matching is a pairing problem, not a set-uniqueness problem. If the overlap window holds
 *       <i>n</i> matching lines in statement A and <i>m</i> in statement B, the answer is {@code
 *       max(n, m)} — the larger of the two accounts of the same days — not {@code n + m} and not 1.
 *       This falls out of pairing each of B's lines with at most one unclaimed line of A: {@code n
 *       + m - min(n, m) = max(n, m)}.
 *   <li>An amount must match <b>exactly</b>, sign included. Fuzzy amounts would merge a ₺50 refund
 *       into the ₺50 charge it reverses; because the signs differ they never share a bucket (SPEC
 *       §4.3).
 *   <li>Nothing is deleted. The later occurrence is reported so it can be flagged {@code
 *       suspected_duplicate} and reviewed. A finance tool that silently drops rows is not one you
 *       can trust with the rows it kept.
 * </ul>
 */
public final class DuplicateDetector {

  /**
   * Token-set Jaccard needed before two lines in the same exact (date, amount, currency) bucket are
   * called the same purchase.
   *
   * <p>0.60 was chosen against the fixture corpus by looking at what the two kinds of difference
   * actually cost. A statement that adds or drops one qualifier on a three-token merchant string —
   * "MIGROS TICARET" versus "MIGROS TICARET ISTANBUL" — scores 0.67, so genuine repeats survive one
   * token of drift. Two unrelated merchants that happen to share a generic token on the same day
   * for the same amount — "AKBANK ATM" versus "AKBANK KOMISYON" — score 0.33, comfortably below.
   * The gap between those two populations is wide, which is why an exact bucket on date and amount
   * comes first: it does the discriminating, and the description only has to survive rewording.
   */
  public static final double JACCARD_THRESHOLD = 0.60;

  /**
   * Runs the pass over every statement given. Statements are sorted into a canonical order first,
   * so the report does not depend on the order they were uploaded or read from the database.
   */
  public DedupReport detect(List<ParsedStatement> statements) {
    List<ParsedStatement> ordered = new ArrayList<>(statements);
    ordered.sort(
        Comparator.comparing((ParsedStatement s) -> s.period().end())
            .thenComparing(s -> s.period().start())
            .thenComparing(s -> s.id().toString()));

    Map<BucketKey, List<Candidate>> buckets = new HashMap<>();
    List<DuplicateMatch> matches = new ArrayList<>();

    for (ParsedStatement statement : ordered) {
      // An earlier line may be claimed by at most one line of the statement being processed.
      // Scoping the claim to this statement is what makes three overlapping statements of the
      // same purchase collapse to one, instead of the third copy surviving because the second
      // already used up the original.
      Set<TransactionId> claimed = new HashSet<>();

      for (Transaction transaction : statement.transactions()) {
        BucketKey key = BucketKey.of(transaction);
        List<Candidate> bucket = buckets.computeIfAbsent(key, k -> new ArrayList<>());

        Optional<Scored> best = bestMatch(statement, transaction, bucket, claimed);
        if (best.isPresent()) {
          Scored scored = best.get();
          claimed.add(scored.candidate().transaction().id());
          matches.add(
              new DuplicateMatch(
                  transaction.id(),
                  statement.id(),
                  scored.candidate().transaction().id(),
                  scored.candidate().statementId(),
                  scored.similarity(),
                  scored.reason()));
          // A suspected duplicate does not itself become an original for a later statement to
          // match against; the original it points at stays available instead.
          continue;
        }
        bucket.add(new Candidate(transaction, statement.id(), statement));
      }
    }

    return new DedupReport(matches);
  }

  private Optional<Scored> bestMatch(
      ParsedStatement statement,
      Transaction transaction,
      List<Candidate> bucket,
      Set<TransactionId> claimed) {

    Scored best = null;
    for (Candidate candidate : bucket) {
      if (candidate.statementId().equals(statement.id())) {
        continue; // never within one statement
      }
      if (claimed.contains(candidate.transaction().id())) {
        continue;
      }
      if (!statement.period().overlaps(candidate.statement().period())) {
        continue; // SPEC §3.4 step 1: only overlapping periods are candidates
      }
      Optional<Scored> scored = score(candidate, transaction);
      if (scored.isPresent() && (best == null || scored.get().betterThan(best))) {
        best = scored.get();
      }
    }
    return Optional.ofNullable(best);
  }

  private Optional<Scored> score(Candidate candidate, Transaction transaction) {
    String left = candidate.transaction().normalisedDescription();
    String right = transaction.normalisedDescription();

    double jaccard = Similarity.tokenSetJaccard(left, right);
    double levenshtein = Similarity.normalisedLevenshtein(left, right);

    if (jaccard >= JACCARD_THRESHOLD) {
      return Optional.of(
          new Scored(candidate, jaccard, levenshtein, MatchReason.DESCRIPTION_SIMILARITY));
    }
    // Truncation is the one common difference a set measure cannot see: the same merchant cut off
    // at two different column widths shares few whole tokens but is unmistakable as a prefix.
    boolean truncated =
        left.length() <= right.length()
            ? Similarity.isTruncationOf(left, right)
            : Similarity.isTruncationOf(right, left);
    if (truncated) {
      return Optional.of(
          new Scored(candidate, jaccard, levenshtein, MatchReason.TRUNCATED_DESCRIPTION));
    }
    return Optional.empty();
  }

  /** Exact (date, amount, currency). SPEC §3.4 step 3: no fuzzy matching on any of the three. */
  private record BucketKey(LocalDate date, String currency, BigDecimal amount) {
    static BucketKey of(Transaction transaction) {
      return new BucketKey(
          transaction.transactionDate(),
          transaction.amount().currency().getCurrencyCode(),
          transaction.amount().amount());
    }
  }

  private record Candidate(
      Transaction transaction, StatementId statementId, ParsedStatement statement) {}

  private record Scored(
      Candidate candidate, double similarity, double levenshtein, MatchReason reason) {

    /** Jaccard decides; normalised Levenshtein breaks ties between equally good candidates. */
    boolean betterThan(Scored other) {
      if (similarity != other.similarity) {
        return similarity > other.similarity;
      }
      if (levenshtein != other.levenshtein) {
        return levenshtein > other.levenshtein;
      }
      return candidate
              .transaction()
              .id()
              .toString()
              .compareTo(other.candidate.transaction().id().toString())
          < 0;
    }
  }
}
