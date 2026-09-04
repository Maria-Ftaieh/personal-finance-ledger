package dev.ledger.app.domain;

import dev.ledger.core.rules.CategorisationRule;
import dev.ledger.core.rules.CategoryId;
import dev.ledger.core.rules.MatchType;
import dev.ledger.core.rules.RuleId;
import dev.ledger.core.rules.SubcategoryId;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "categorisation_rules")
public class RuleEntity {

  @Id private UUID id;

  @Column(name = "user_id", nullable = false)
  private UUID userId;

  @Column(nullable = false)
  private int priority;

  @Enumerated(EnumType.STRING)
  @Column(name = "match_type", nullable = false, length = 16)
  private MatchType matchType;

  @Column(nullable = false, length = 200)
  private String pattern;

  @Column(name = "category_id", nullable = false, length = 64)
  private String categoryId;

  @Column(name = "subcategory_id", length = 128)
  private String subcategoryId;

  @Column(name = "user_defined", nullable = false)
  private boolean userDefined;

  protected RuleEntity() {}

  public RuleEntity(
      UUID id,
      UUID userId,
      int priority,
      MatchType matchType,
      String pattern,
      String categoryId,
      String subcategoryId,
      boolean userDefined) {
    this.id = id;
    this.userId = userId;
    this.userDefined = userDefined;
    update(priority, matchType, pattern, categoryId, subcategoryId);
  }

  /**
   * Applies an edit. The domain record's constructor validates the pattern — including running a
   * user regex past {@link dev.ledger.core.rules.RegexGuard} — so an unsafe rule cannot reach the
   * database through this path (SPEC §5.3).
   */
  public final void update(
      int priority, MatchType matchType, String pattern, String categoryId, String subcategoryId) {
    this.priority = priority;
    this.matchType = matchType;
    this.pattern = pattern;
    this.categoryId = categoryId;
    this.subcategoryId = subcategoryId;
    toDomain();
  }

  public CategorisationRule toDomain() {
    return new CategorisationRule(
        new RuleId(id),
        priority,
        matchType,
        pattern,
        CategoryId.of(categoryId),
        subcategoryId == null ? null : SubcategoryId.of(subcategoryId),
        userDefined);
  }

  public UUID getId() {
    return id;
  }

  public int getPriority() {
    return priority;
  }

  public MatchType getMatchType() {
    return matchType;
  }

  public String getPattern() {
    return pattern;
  }

  public String getCategoryId() {
    return categoryId;
  }

  public String getSubcategoryId() {
    return subcategoryId;
  }

  public boolean isUserDefined() {
    return userDefined;
  }
}
