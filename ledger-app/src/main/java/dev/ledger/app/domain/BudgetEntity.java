package dev.ledger.app.domain;

import dev.ledger.core.money.Money;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.Currency;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** A recurring monthly spending limit for one category. SPEC §6.5. */
@Entity
@Table(name = "budgets")
public class BudgetEntity {

  @Id private UUID id;

  @Column(name = "user_id", nullable = false)
  private UUID userId;

  @Column(name = "category_id", nullable = false, length = 64)
  private String categoryId;

  @Column(nullable = false, precision = 19, scale = 4)
  private BigDecimal amount;

  @JdbcTypeCode(SqlTypes.CHAR)
  @Column(nullable = false, length = 3, columnDefinition = "char(3)")
  private String currency;

  protected BudgetEntity() {}

  /**
   * Validates first, then constructs.
   *
   * <p>The constructor deliberately cannot throw: an exception part-way through building an object
   * leaves it half-initialised, and SpotBugs flags the pattern. Checking the argument before any
   * field is assigned keeps the invariant without that.
   */
  public static BudgetEntity create(UUID id, UUID userId, String categoryId, Money limit) {
    requirePositive(limit);
    return new BudgetEntity(id, userId, categoryId, limit);
  }

  private BudgetEntity(UUID id, UUID userId, String categoryId, Money limit) {
    this.id = id;
    this.userId = userId;
    this.categoryId = categoryId;
    this.amount = limit.amount();
    this.currency = limit.currency().getCurrencyCode();
  }

  public void setLimit(Money limit) {
    requirePositive(limit);
    this.amount = limit.amount();
    this.currency = limit.currency().getCurrencyCode();
  }

  private static void requirePositive(Money limit) {
    if (!limit.isPositive()) {
      throw new IllegalArgumentException("a budget must be positive, was " + limit);
    }
  }

  public UUID getId() {
    return id;
  }

  public String getCategoryId() {
    return categoryId;
  }

  public Money getLimit() {
    return Money.of(amount, Currency.getInstance(currency));
  }
}
