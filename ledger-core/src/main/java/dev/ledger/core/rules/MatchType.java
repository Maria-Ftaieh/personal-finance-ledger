package dev.ledger.core.rules;

/** How a rule's pattern is compared against a normalised description. */
public enum MatchType {
  CONTAINS,
  STARTS_WITH,
  EXACT,
  /** User-authored regular expression. Guarded by {@link RegexGuard}; never trusted as given. */
  REGEX
}
