package dev.ledger.app.web;

import dev.ledger.app.domain.BudgetAlertEntity;
import dev.ledger.app.domain.BudgetEntity;
import dev.ledger.app.service.BudgetService;
import dev.ledger.app.service.ReportService;
import dev.ledger.core.money.Money;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.time.YearMonth;
import java.util.Currency;
import java.util.List;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Monthly limits and the alerts they raise. SPEC §6.5. */
@RestController
@RequestMapping("/api")
public class BudgetController {

  private final BudgetService budgets;
  private final ReportService reports;

  public BudgetController(BudgetService budgets, ReportService reports) {
    this.budgets = budgets;
    this.reports = reports;
  }

  @GetMapping("/budgets")
  public List<BudgetView> list() {
    return budgets.list().stream().map(BudgetView::of).toList();
  }

  /** Sets the monthly limit for a category, then re-checks every month that has spending. */
  @PutMapping("/budgets/{categoryId}")
  public BudgetView setLimit(@PathVariable String categoryId, @RequestBody BudgetRequest body) {
    BudgetEntity saved =
        budgets.setLimit(
            categoryId, Money.of(body.amount(), Currency.getInstance(body.currency())));
    budgets.evaluate(reports.monthsWithSpending());
    return BudgetView.of(saved);
  }

  @DeleteMapping("/budgets/{categoryId}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void remove(@PathVariable String categoryId) {
    budgets.remove(categoryId);
  }

  /**
   * @param month optional; omit for every standing alert, newest month first
   */
  @GetMapping("/alerts")
  public List<AlertView> alerts(
      @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM") YearMonth month) {
    return budgets.alerts(month).stream().map(AlertView::of).toList();
  }

  public record BudgetRequest(@NotBlank String amount, @NotNull String currency) {}

  public record BudgetView(UUID id, String categoryId, Money limit) {
    static BudgetView of(BudgetEntity entity) {
      return new BudgetView(entity.getId(), entity.getCategoryId(), entity.getLimit());
    }
  }

  public record AlertView(
      UUID id,
      String categoryId,
      YearMonth month,
      Money budget,
      Money spent,
      Money overspend,
      Instant raisedAt) {

    static AlertView of(BudgetAlertEntity entity) {
      return new AlertView(
          entity.getId(),
          entity.getCategoryId(),
          entity.getMonth(),
          entity.getBudget(),
          entity.getSpent(),
          entity.getOverspend(),
          entity.getRaisedAt());
    }
  }
}
