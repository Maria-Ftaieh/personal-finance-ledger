package dev.ledger.core.rules;

import dev.ledger.core.model.Transaction;
import dev.ledger.core.text.DescriptionNormalizer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Assigns a category to a transaction.
 *
 * <p><b>First match wins.</b> SPEC §5.3. Rules are evaluated in priority order — lower number
 * first, {@link RuleId} breaking ties so the order is total and stable — and the first one that
 * matches decides the category. The alternative, letting every matching rule contribute, produces
 * totals that do not add up: a coffee that is both "Kahve" and "Restoran" would be counted twice in
 * a category breakdown, and the monthly total would exceed what the user actually spent. One
 * transaction, one category.
 *
 * <p>Matching runs against {@link Transaction#normalisedDescription()}, which is folded to
 * upper-case ASCII with {@link java.util.Locale#ROOT} (§3.2). Literal patterns are folded the same
 * way here, so a user may type a rule as "kahve dünyası" and have it match the stored "KAHVE
 * DUNYASI".
 *
 * <p>The engine assigns nothing by itself. A transaction the user has categorised by hand carries
 * an override flag, and the caller applies a rule's verdict only in its absence — re-evaluating the
 * rule set must never undo a human's correction.
 */
public final class RuleEngine {

  private final List<CategorisationRule> ordered;
  private final Map<RuleId, Pattern> compiledRegexes;
  private final Map<RuleId, String> foldedPatterns;

  public RuleEngine(Collection<CategorisationRule> rules) {
    List<CategorisationRule> sorted = new ArrayList<>(rules);
    sorted.sort(
        Comparator.comparingInt(CategorisationRule::priority)
            .thenComparing(CategorisationRule::id));
    this.ordered = List.copyOf(sorted);

    Map<RuleId, Pattern> regexes = new HashMap<>();
    Map<RuleId, String> folded = new HashMap<>();
    for (CategorisationRule rule : this.ordered) {
      if (rule.matchType() == MatchType.REGEX) {
        regexes.put(rule.id(), RegexGuard.compile(rule.pattern()));
      } else {
        folded.put(rule.id(), DescriptionNormalizer.normalise(rule.pattern()));
      }
    }
    this.compiledRegexes = Map.copyOf(regexes);
    this.foldedPatterns = Map.copyOf(folded);
  }

  /** The rules in the order they are evaluated. */
  public List<CategorisationRule> rules() {
    return ordered;
  }

  public Optional<CategorisationRule> match(Transaction transaction) {
    return match(transaction.normalisedDescription());
  }

  /**
   * @param normalisedDescription must already be {@link DescriptionNormalizer} output
   */
  public Optional<CategorisationRule> match(String normalisedDescription) {
    String description = normalisedDescription == null ? "" : normalisedDescription;
    for (CategorisationRule rule : ordered) {
      if (matches(rule, description)) {
        return Optional.of(rule);
      }
    }
    return Optional.empty();
  }

  private boolean matches(CategorisationRule rule, String description) {
    if (rule.matchType() == MatchType.REGEX) {
      return RegexGuard.findWithin(compiledRegexes.get(rule.id()), description);
    }
    String pattern = foldedPatterns.get(rule.id());
    if (pattern.isEmpty()) {
      return false;
    }
    return switch (rule.matchType()) {
      case CONTAINS -> description.contains(pattern);
      case STARTS_WITH -> description.startsWith(pattern);
      case EXACT -> description.equals(pattern);
      case REGEX -> throw new IllegalStateException("handled above");
    };
  }
}
