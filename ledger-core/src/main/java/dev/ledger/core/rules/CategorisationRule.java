package dev.ledger.core.rules;

import java.util.Objects;

/**
 * A rule assigning a category to transactions whose normalised description matches a pattern.
 *
 * <p>SPEC §5.3.
 *
 * @param priority lower wins; the space is <b>not</b> partitioned by ownership, so a user rule can
 *     be given priority 1 and outrank every seeded rule. Reserving a band for system rules would
 *     make the one thing users most want to do — override a bad guess — impossible.
 * @param userDefined whether the user created it. Presentation and audit only; it has no effect on
 *     evaluation order.
 */
public record CategorisationRule(
    RuleId id,
    int priority,
    MatchType matchType,
    String pattern,
    CategoryId category,
    SubcategoryId subcategory,
    boolean userDefined) {

  public CategorisationRule {
    Objects.requireNonNull(id, "id");
    Objects.requireNonNull(matchType, "matchType");
    Objects.requireNonNull(pattern, "pattern");
    Objects.requireNonNull(category, "category");
    if (pattern.isBlank()) {
      throw new IllegalArgumentException("rule pattern must not be blank");
    }
    if (matchType == MatchType.REGEX) {
      // Reject a dangerous pattern at save time rather than at match time (SPEC §5.3).
      RegexGuard.compile(pattern);
    }
  }
}
