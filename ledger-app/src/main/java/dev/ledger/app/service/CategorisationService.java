package dev.ledger.app.service;

import dev.ledger.app.config.CurrentUser;
import dev.ledger.app.domain.RuleEntity;
import dev.ledger.app.domain.TransactionEntity;
import dev.ledger.app.repo.CategoryRepository;
import dev.ledger.app.repo.RuleRepository;
import dev.ledger.app.repo.SubcategoryRepository;
import dev.ledger.app.repo.TransactionRepository;
import dev.ledger.core.rules.CategorisationRule;
import dev.ledger.core.rules.RuleEngine;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Applies the rule set to transactions.
 *
 * <p>The engine itself lives in {@code ledger-core} and knows nothing about Spring or JPA. This
 * class only loads the rules, hands them over, and writes the verdict back.
 */
@Service
public class CategorisationService {

  /** Where a transaction lands when no rule matches. Seeded as a system category (SPEC §5.3). */
  public static final String UNCATEGORISED_CATEGORY = "diger";

  public static final String UNCATEGORISED_SUBCATEGORY = "diger.siniflandirilmamis";

  private final RuleRepository rules;
  private final TransactionRepository transactions;
  private final CategoryRepository categories;
  private final SubcategoryRepository subcategories;

  public CategorisationService(
      RuleRepository rules,
      TransactionRepository transactions,
      CategoryRepository categories,
      SubcategoryRepository subcategories) {
    this.rules = rules;
    this.transactions = transactions;
    this.categories = categories;
    this.subcategories = subcategories;
  }

  /**
   * The user categorises one transaction by hand.
   *
   * <p>SPEC §5.3: this must survive rule re-evaluation. It is stored as an override flag rather
   * than as a rule, because it is a statement about this transaction and not a generalisation the
   * user asked for — inventing a rule from one correction would silently recategorise everything
   * that happens to look similar.
   */
  @Transactional
  public TransactionEntity assignManually(
      UUID transactionId, String categoryId, String subcategoryId) {
    if (!categories.existsById(categoryId)) {
      throw new IllegalArgumentException("no such category: " + categoryId);
    }
    if (subcategoryId != null && !subcategories.existsById(subcategoryId)) {
      throw new IllegalArgumentException("no such subcategory: " + subcategoryId);
    }
    TransactionEntity row = require(transactionId);
    row.overrideCategory(categoryId, subcategoryId);
    return transactions.save(row);
  }

  /** Drops the manual assignment and hands the transaction back to the rules. */
  @Transactional
  public TransactionEntity clearManualAssignment(UUID transactionId) {
    TransactionEntity row = require(transactionId);
    row.clearCategoryOverride();
    apply(engine(), List.of(row));
    return transactions.save(row);
  }

  private TransactionEntity require(UUID transactionId) {
    return transactions
        .findById(transactionId)
        .filter(row -> row.getUserId().equals(CurrentUser.ID))
        .orElseThrow(() -> new NoSuchElementException("no transaction " + transactionId));
  }

  /**
   * Builds an engine from the current rule set. Rebuilt per operation rather than cached: rule sets
   * are tens of rows, the cost is one query and a few regex compiles, and a cache would need
   * invalidating on every rule edit for no measurable gain.
   */
  public RuleEngine engine() {
    List<CategorisationRule> current =
        rules.findByUserIdOrderByPriorityAscIdAsc(CurrentUser.ID).stream()
            .map(RuleEntity::toDomain)
            .toList();
    return new RuleEngine(current);
  }

  /** Categorises rows that have no manual override. Returns how many changed. */
  public int apply(RuleEngine engine, List<TransactionEntity> rows) {
    int changed = 0;
    for (TransactionEntity row : rows) {
      if (row.isCategoryOverride()) {
        continue;
      }
      Verdict verdict = verdictFor(engine, row);
      if (row.applyCategory(verdict.category(), verdict.subcategory())) {
        changed++;
      }
    }
    return changed;
  }

  /**
   * How many rows a rule change would move, without moving them.
   *
   * <p>SPEC §5.3 requires this before committing a bulk recategorisation. Editing a rule can
   * silently reshape a year of history, and a number to look at first is the difference between a
   * deliberate change and an accident.
   */
  @Transactional(readOnly = true)
  public Preview preview() {
    RuleEngine engine = engine();
    List<TransactionEntity> all = transactions.findByUserId(CurrentUser.ID);

    int wouldChange = 0;
    int held = 0;
    for (TransactionEntity row : all) {
      if (row.isCategoryOverride()) {
        held++;
        continue;
      }
      Verdict verdict = verdictFor(engine, row);
      if (!verdict.category().equals(row.getCategoryId())
          || !Objects.equals(verdict.subcategory(), row.getSubcategoryId())) {
        wouldChange++;
      }
    }
    return new Preview(all.size(), wouldChange, held);
  }

  /** Re-evaluates every transaction against the current rules. Returns how many changed. */
  @Transactional
  public int reevaluateAll() {
    List<TransactionEntity> all = transactions.findByUserId(CurrentUser.ID);
    int changed = apply(engine(), all);
    transactions.saveAll(all);
    return changed;
  }

  private static Verdict verdictFor(RuleEngine engine, TransactionEntity row) {
    Optional<CategorisationRule> match = engine.match(row.getNormalisedDescription());
    return new Verdict(
        match.map(rule -> rule.category().value()).orElse(UNCATEGORISED_CATEGORY),
        match
            .map(rule -> rule.subcategory() == null ? null : rule.subcategory().value())
            .orElse(UNCATEGORISED_SUBCATEGORY));
  }

  private record Verdict(String category, String subcategory) {}

  /**
   * @param examined every transaction the user has
   * @param wouldChange how many would be assigned a different category
   * @param heldByOverride how many are protected by a manual assignment and will not move
   */
  public record Preview(int examined, int wouldChange, int heldByOverride) {}
}
