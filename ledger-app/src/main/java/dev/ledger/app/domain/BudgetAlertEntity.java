package dev.ledger.app.domain;

import dev.ledger.core.money.Money;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Currency;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * A budget that was exceeded in a given month.
 *
 * <p>SPEC §6.5: evaluated on write and stored, so the endpoint reads a row rather than recomputing
 * every category against every month on each request. There is deliberately no email or push.
 */
@Entity
@Table(name = "budget_alerts")
public class BudgetAlertEntity {

  @Id private UUID id;

  @Column(name = "user_id", nullable = false)
  private UUID userId;

  @Column(name = "category_id", nullable = false, length = 64)
  private String categoryId;

  @Column(nullable = false)
  private LocalDate month;

  @Column(name = "budget_amount", nullable = false, precision = 19, scale = 4)
  private BigDecimal budgetAmount;

  @Column(name = "spent_amount", nullable = false, precision = 19, scale = 4)
  private BigDecimal spentAmount;

  @JdbcTypeCode(SqlTypes.CHAR)
  @Column(nullable = false, length = 3, columnDefinition = "char(3)")
  private String currency;

  @Column(name = "raised_at", nullable = false)
  private Instant raisedAt;

  protected BudgetAlertEntity() {}

  public BudgetAlertEntity(
      UUID id, UUID userId, String categoryId, YearMonth month, Money budget, Money spent) {
    this.id = id;
    this.userId = userId;
    this.categoryId = categoryId;
    this.month = month.atDay(1);
    this.currency = budget.currency().getCurrencyCode();
    this.raisedAt = Instant.now();
    update(budget, spent);
  }

  /** Re-evaluation refreshes the figures on the standing alert rather than raising another. */
  public final void update(Money budget, Money spent) {
    this.budgetAmount = budget.amount();
    this.spentAmount = spent.amount();
    this.raisedAt = Instant.now();
  }

  public UUID getId() {
    return id;
  }

  public String getCategoryId() {
    return categoryId;
  }

  public YearMonth getMonth() {
    return YearMonth.from(month);
  }

  public Money getBudget() {
    return Money.of(budgetAmount, Currency.getInstance(currency));
  }

  public Money getSpent() {
    return Money.of(spentAmount, Currency.getInstance(currency));
  }

  public Money getOverspend() {
    return getSpent().minus(getBudget());
  }

  public Instant getRaisedAt() {
    return raisedAt;
  }
}
