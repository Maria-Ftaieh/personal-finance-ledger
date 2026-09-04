package dev.ledger.core.rules;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Makes user-authored regular expressions safe to run.
 *
 * <p>SPEC §5.3. Java's regex engine backtracks, so a pattern such as {@code (a+)+b} against a
 * string of forty {@code a}s runs for longer than the universe has existed. That this is a personal
 * tool is not a reason to skip the guard: the user is the one who will paste a pattern from the
 * internet, and the failure mode is a wedged thread with no error, which is far harder to diagnose
 * than a rejected rule.
 *
 * <p>Two defences, because either alone is insufficient:
 *
 * <ol>
 *   <li><b>A complexity budget at save time.</b> Rejects the shapes that cause exponential
 *       backtracking — a quantifier applied to a group that itself contains a quantifier — before
 *       the pattern is ever stored.
 *   <li><b>A step budget at match time.</b> The input is wrapped in a {@link CharSequence} that
 *       counts reads and throws once they exceed a bound proportional to the input length. A
 *       backtracking explosion re-reads the same characters millions of times, so this catches
 *       anything the static check missed, and does it without a watchdog thread.
 * </ol>
 */
public final class RegexGuard {

  /**
   * Character reads allowed per input character before a match is abandoned. A linear-time match
   * reads each character a small constant number of times; 2 000 is generous for a legitimate
   * pattern on a merchant string and reached in milliseconds by a pathological one.
   */
  static final int STEP_BUDGET_PER_CHARACTER = 2_000;

  /**
   * Longer patterns than this are refused outright; a categorisation rule needs nowhere near it.
   */
  static final int MAX_PATTERN_LENGTH = 200;

  /** A quantified group that itself contains a quantifier: the classic exponential shape. */
  private static final Pattern NESTED_QUANTIFIER =
      Pattern.compile(
          "\\((?:[^()]*[*+]|[^()]*\\{\\d+,\\d*})[^()]*\\)\\s*[*+]|\\((?:[^()]*[*+])[^()]*\\)\\s*\\{");

  private RegexGuard() {}

  /**
   * Validates and compiles a user pattern.
   *
   * @throws IllegalArgumentException if the pattern is malformed or outside the complexity budget
   */
  public static Pattern compile(String pattern) {
    if (pattern == null || pattern.isBlank()) {
      throw new IllegalArgumentException("regex rule has no pattern");
    }
    if (pattern.length() > MAX_PATTERN_LENGTH) {
      throw new IllegalArgumentException(
          "regex pattern is longer than " + MAX_PATTERN_LENGTH + " characters");
    }
    if (NESTED_QUANTIFIER.matcher(pattern).find()) {
      throw new IllegalArgumentException(
          "regex pattern nests a quantifier inside a quantified group, which can backtrack "
              + "catastrophically: "
              + pattern);
    }
    try {
      // CASE_INSENSITIVE only: descriptions reach the engine already folded to upper-case ASCII by
      // DescriptionNormalizer, so UNICODE_CASE would buy nothing and would drag the Turkish
      // case-mapping rules back into matching logic, which §3.2 exists to keep out.
      return Pattern.compile(pattern, Pattern.CASE_INSENSITIVE);
    } catch (PatternSyntaxException e) {
      throw new IllegalArgumentException(
          "regex pattern does not compile: " + e.getDescription(), e);
    }
  }

  /** Runs a compiled pattern under the step budget. Returns false if the budget is exhausted. */
  public static boolean findWithin(Pattern pattern, String input) {
    Matcher matcher = pattern.matcher(new BudgetedCharSequence(input, budgetFor(input)));
    try {
      return matcher.find();
    } catch (StepBudgetExceededException e) {
      return false;
    }
  }

  private static long budgetFor(String input) {
    return (long) Math.max(input.length(), 1) * STEP_BUDGET_PER_CHARACTER;
  }

  /**
   * Signals that a match was abandoned. Package private: callers see {@code false}, not a throw.
   */
  static final class StepBudgetExceededException extends RuntimeException {
    @java.io.Serial private static final long serialVersionUID = 1L;

    StepBudgetExceededException() {
      super("regex step budget exceeded", null, false, false);
    }
  }

  /**
   * A view of the input that counts how often the regex engine reads a character and gives up once
   * the count passes the budget. This is the cheapest reliable way to bound a Java regex: the
   * engine has no timeout of its own, and interrupting it needs a second thread.
   */
  private static final class BudgetedCharSequence implements CharSequence {
    private final CharSequence delegate;
    private final long budget;
    private long reads;

    BudgetedCharSequence(CharSequence delegate, long budget) {
      this(delegate, budget, 0);
    }

    private BudgetedCharSequence(CharSequence delegate, long budget, long reads) {
      this.delegate = delegate;
      this.budget = budget;
      this.reads = reads;
    }

    @Override
    public char charAt(int index) {
      if (++reads > budget) {
        throw new StepBudgetExceededException();
      }
      return delegate.charAt(index);
    }

    @Override
    public int length() {
      return delegate.length();
    }

    @Override
    public CharSequence subSequence(int start, int end) {
      return new BudgetedCharSequence(delegate.subSequence(start, end), budget, reads);
    }

    @Override
    public String toString() {
      return delegate.toString();
    }
  }
}
