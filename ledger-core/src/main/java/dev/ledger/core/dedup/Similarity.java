package dev.ledger.core.dedup;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/** String similarity measures used to decide whether two statement lines describe one purchase. */
public final class Similarity {

  private static final Pattern SPACE = Pattern.compile(" ");

  private Similarity() {}

  public static List<String> tokens(String normalised) {
    if (normalised.isEmpty()) {
      return List.of();
    }
    return List.of(SPACE.split(normalised));
  }

  /**
   * Token-set Jaccard: shared tokens over total distinct tokens.
   *
   * <p>Chosen as the primary measure because the differences between two printings of the same
   * purchase are usually whole tokens — an extra branch name, a dropped city, a reordered suffix —
   * and a set measure is blind to token order, which character distance is not.
   *
   * <p>Two empty descriptions score 1.0; that is only reachable when both statements printed
   * nothing, and the exact date/amount/currency bucket has already done the real work.
   */
  public static double tokenSetJaccard(String a, String b) {
    Set<String> left = new LinkedHashSet<>(tokens(a));
    Set<String> right = new LinkedHashSet<>(tokens(b));
    if (left.isEmpty() && right.isEmpty()) {
      return 1.0;
    }
    if (left.isEmpty() || right.isEmpty()) {
      return 0.0;
    }
    Set<String> union = new LinkedHashSet<>(left);
    union.addAll(right);
    int shared = 0;
    for (String token : left) {
      if (right.contains(token)) {
        shared++;
      }
    }
    return (double) shared / union.size();
  }

  /**
   * Levenshtein distance scaled to {@code [0,1]}, where 1.0 is identical. Used only to rank
   * candidates that already passed the primary test — it is the tiebreaker, not the gate.
   */
  public static double normalisedLevenshtein(String a, String b) {
    int longest = Math.max(a.length(), b.length());
    if (longest == 0) {
      return 1.0;
    }
    return 1.0 - (double) levenshtein(a, b) / longest;
  }

  /** Two-row dynamic programme; descriptions are short, so this is comfortably cheap. */
  static int levenshtein(String a, String b) {
    int[] previous = new int[b.length() + 1];
    int[] current = new int[b.length() + 1];
    for (int j = 0; j <= b.length(); j++) {
      previous[j] = j;
    }
    for (int i = 1; i <= a.length(); i++) {
      current[0] = i;
      for (int j = 1; j <= b.length(); j++) {
        int substitution = previous[j - 1] + (a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1);
        current[j] = Math.min(substitution, Math.min(previous[j] + 1, current[j - 1] + 1));
      }
      int[] swap = previous;
      previous = current;
      current = swap;
    }
    return previous[b.length()];
  }

  /**
   * True when {@code shorter} looks like {@code longer} cut off by a column width: its tokens are a
   * prefix of the other's, with the final token allowed to be cut mid-word.
   *
   * <p>Truncation defeats Jaccard — "MIGROS TICARET AS ISTANBUL" against "MIGROS TICARET A" scores
   * 0.4 — but it is one of the most common ways the same purchase differs between two statements,
   * because the two documents lay the description column out at different widths.
   */
  public static boolean isTruncationOf(String shorter, String longer) {
    List<String> shortTokens = tokens(shorter);
    List<String> longTokens = tokens(longer);
    if (shortTokens.size() < MIN_PREFIX_TOKENS || shortTokens.size() > longTokens.size()) {
      return false;
    }
    for (int i = 0; i < shortTokens.size() - 1; i++) {
      if (!shortTokens.get(i).equals(longTokens.get(i))) {
        return false;
      }
    }
    String lastShort = shortTokens.get(shortTokens.size() - 1);
    String lastLong = longTokens.get(shortTokens.size() - 1);
    boolean lastTokenAgrees =
        lastShort.equals(lastLong)
            || (shortTokens.size() < longTokens.size() && lastLong.startsWith(lastShort));
    return lastTokenAgrees && shorter.length() >= MIN_PREFIX_CHARS;
  }

  /**
   * A truncated description must keep at least two whole tokens and this many characters before it
   * is allowed to match. Below that, "AKB" would match every merchant starting with those letters,
   * and on a same-day same-amount bucket that is a real risk rather than a theoretical one.
   */
  static final int MIN_PREFIX_CHARS = 10;

  static final int MIN_PREFIX_TOKENS = 2;
}
