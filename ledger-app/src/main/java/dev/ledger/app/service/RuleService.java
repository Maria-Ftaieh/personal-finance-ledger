package dev.ledger.app.service;

import dev.ledger.app.config.CurrentUser;
import dev.ledger.app.domain.RuleEntity;
import dev.ledger.app.repo.CategoryRepository;
import dev.ledger.app.repo.RuleRepository;
import dev.ledger.app.repo.SubcategoryRepository;
import dev.ledger.core.rules.MatchType;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Rule management.
 *
 * <p>The dangerous part of a rule is its pattern, and that is validated in {@code ledger-core}:
 * {@link dev.ledger.core.rules.CategorisationRule}'s constructor runs a user regex past {@link
 * dev.ledger.core.rules.RegexGuard}, so a pattern that could backtrack catastrophically is rejected
 * on the way in and can never be stored.
 */
@Service
public class RuleService {

  private final RuleRepository rules;
  private final CategoryRepository categories;
  private final SubcategoryRepository subcategories;

  public RuleService(
      RuleRepository rules, CategoryRepository categories, SubcategoryRepository subcategories) {
    this.rules = rules;
    this.categories = categories;
    this.subcategories = subcategories;
  }

  @Transactional(readOnly = true)
  public List<RuleEntity> list() {
    return rules.findByUserIdOrderByPriorityAscIdAsc(CurrentUser.ID);
  }

  @Transactional
  public RuleEntity create(RuleDraft draft) {
    validate(draft);
    return rules.save(
        new RuleEntity(
            UUID.randomUUID(),
            CurrentUser.ID,
            draft.priority(),
            draft.matchType(),
            draft.pattern(),
            draft.categoryId(),
            draft.subcategoryId(),
            true));
  }

  @Transactional
  public RuleEntity update(UUID id, RuleDraft draft) {
    validate(draft);
    RuleEntity rule = require(id);
    rule.update(
        draft.priority(),
        draft.matchType(),
        draft.pattern(),
        draft.categoryId(),
        draft.subcategoryId());
    return rules.save(rule);
  }

  @Transactional
  public void delete(UUID id) {
    rules.delete(require(id));
  }

  private RuleEntity require(UUID id) {
    return rules
        .findByIdAndUserId(id, CurrentUser.ID)
        .orElseThrow(() -> new NoSuchElementException("no rule " + id));
  }

  private void validate(RuleDraft draft) {
    if (!categories.existsById(draft.categoryId())) {
      throw new IllegalArgumentException("no such category: " + draft.categoryId());
    }
    if (draft.subcategoryId() != null && !subcategories.existsById(draft.subcategoryId())) {
      throw new IllegalArgumentException("no such subcategory: " + draft.subcategoryId());
    }
  }

  /**
   * @param priority lower wins. Nothing reserves a band for seeded rules: a user rule may take
   *     priority 1 and outrank all of them (SPEC §5.3).
   */
  public record RuleDraft(
      int priority, MatchType matchType, String pattern, String categoryId, String subcategoryId) {}
}
