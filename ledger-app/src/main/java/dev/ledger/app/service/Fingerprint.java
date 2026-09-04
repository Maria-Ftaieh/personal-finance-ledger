package dev.ledger.app.service;

import dev.ledger.core.model.Transaction;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;

/**
 * Identifies one row of one statement file.
 *
 * <p>SPEC §5.2 puts a unique constraint on this, scoped by user, so that re-uploading the same file
 * is a no-op. That requirement pulls in two directions and the {@code occurrence} argument is what
 * reconciles them: the fingerprint has to be <em>stable</em> across a re-upload of identical bytes,
 * and <em>distinct</em> for two identical purchases on the same day, which §3.4 insists are two
 * real purchases and not one.
 *
 * <p>So the statement's own content hash is part of the input — the same file always produces the
 * same fingerprints — and identical rows within that file are numbered. This is a uniqueness key
 * for a row of a document, not an identity for a purchase. Matching the same purchase printed on
 * two different documents is deduplication's job, and it is deliberately a scored, reviewable
 * decision rather than a database constraint.
 */
public final class Fingerprint {

  /** Separator that cannot occur in any of the joined values. */
  private static final String SEPARATOR = "|";

  private Fingerprint() {}

  /**
   * @param occurrence 0 for the first row with these values in this statement, 1 for the next
   *     identical one, and so on
   */
  public static String of(
      UUID userId, String statementContentHash, int occurrence, Transaction row) {
    String material =
        String.join(
            SEPARATOR,
            userId.toString(),
            statementContentHash,
            Integer.toString(occurrence),
            row.transactionDate().toString(),
            row.postingDate().toString(),
            row.amount().toString(),
            row.amount().currency().getCurrencyCode(),
            row.rawDescription());
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256")
                  .digest(material.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 is required by every JVM", e);
    }
  }

  /** The key identical rows are counted by: the values a reader would call "the same line". */
  public static String rowKey(Transaction row) {
    return String.join(
        SEPARATOR,
        row.transactionDate().toString(),
        row.amount().toString(),
        row.amount().currency().getCurrencyCode(),
        row.rawDescription());
  }
}
