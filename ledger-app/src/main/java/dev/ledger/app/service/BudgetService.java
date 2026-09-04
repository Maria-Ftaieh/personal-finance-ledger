package dev.ledger.app.service;

import dev.ledger.app.config.CurrentUser;
import dev.ledger.app.domain.BudgetAlertEntity;
import dev.ledger.app.domain.BudgetEntity;
import dev.ledger.app.repo.BudgetAlertRepository;
import dev.ledger.app.repo.BudgetRepository;
import dev.ledger.app.repo.CategoryRepository;
import dev.ledger.core.money.Money;
import java.time.YearMonth;
import java.util.Collection;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Monthly spending limits, and the alerts raised when one is passed.
 *
 * <p>SPEC §6.5: evaluated on write and stored, exposed as an endpoint. No email, no push.
 *
 * <p>Alerts compare <b>nominal</b> spending against the limit, not real. A budget is a decision the
 * user made about the money that will actually leave their account this month, so deflating either
 * side would answer a question nobody asked. Real terms are for comparing months to each other,
 * which is what the reports do.
 */
@Service
public class BudgetService {

  private final BudgetRepository budgets;
  private final BudgetAlertRepository alerts;
  private final CategoryRepository categories;
  private final ReportService reports;

  public BudgetService(
      BudgetRepository budgets,
      BudgetAlertRepository alerts,
      CategoryRepository categories,
      ReportService reports) {
    this.budgets = budgets;
    this.alerts = alerts;
    this.categories = categories;
    this.reports = reports;
  }

  @Transactional(readOnly = true)
  public List<BudgetEntity> list() {
    return budgets.findByUserIdOrderByCategoryIdAsc(CurrentUser.ID);
  }

  /** Sets or replaces the monthly limit for a category. */
  @Transactional
  public BudgetEntity setLimit(String categoryId, Money limit) {
    if (!categories.existsById(categoryId)) {
      throw new IllegalArgumentException("no such category: " + categoryId);
    }
    BudgetEntity budget =
        budgets
            .findByUserIdAndCategoryId(CurrentUser.ID, categoryId)
            .orElseGet(
                () -> BudgetEntity.create(UUID.randomUUID(), CurrentUser.ID, categoryId, limit));
    budget.setLimit(limit);
    return budgets.save(budget);
  }

  @Transactional
  public void remove(String categoryId) {
    budgets.delete(
        budgets
            .findByUserIdAndCategoryId(CurrentUser.ID, categoryId)
            .orElseThrow(() -> new NoSuchElementException("no budget for category " + categoryId)));
  }

  @Transactional(readOnly = true)
  public List<BudgetAlertEntity> alerts(YearMonth month) {
    return month == null
        ? alerts.findByUserIdOrderByMonthDescCategoryIdAsc(CurrentUser.ID)
        : alerts.findByUserIdAndMonthOrderByCategoryIdAsc(CurrentUser.ID, month.atDay(1));
  }

  /**
   * Re-checks every budget for the given months and stores what is over.
   *
   * <p>Called after an import and after a bulk recategorisation, because both change what a
   * category holds. A category that has come back under its limit has its standing alert cleared —
   * an alert that outlives the overspend is worse than no alert.
   *
   * @return how many alerts stand after the evaluation
   */
  @Transactional
  public int evaluate(Collection<YearMonth> months) {
    List<BudgetEntity> limits = budgets.findByUserIdOrderByCategoryIdAsc(CurrentUser.ID);
    if (limits.isEmpty()) {
      return 0;
    }
    int standing = 0;
    for (YearMonth month : months) {
      for (BudgetEntity budget : limits) {
        Money spent = reports.nominalSpend(month, budget.getCategoryId());
        Optional<BudgetAlertEntity> existing =
            alerts.findByUserIdAndCategoryIdAndMonth(
                CurrentUser.ID, budget.getCategoryId(), month.atDay(1));

        if (spent.compareTo(budget.getLimit()) > 0) {
          BudgetAlertEntity alert =
              existing.orElseGet(
                  () ->
                      new BudgetAlertEntity(
                          UUID.randomUUID(),
                          CurrentUser.ID,
                          budget.getCategoryId(),
                          month,
                          budget.getLimit(),
                          spent));
          alert.update(budget.getLimit(), spent);
          alerts.save(alert);
          standing++;
        } else {
          existing.ifPresent(alerts::delete);
        }
      }
    }
    return standing;
  }
}
