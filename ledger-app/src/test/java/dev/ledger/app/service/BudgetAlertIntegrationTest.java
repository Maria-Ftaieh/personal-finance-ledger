package dev.ledger.app.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.ledger.app.PostgresIntegrationTest;
import dev.ledger.app.domain.BudgetAlertEntity;
import dev.ledger.app.repo.BudgetAlertRepository;
import dev.ledger.app.repo.BudgetRepository;
import dev.ledger.app.repo.StatementRepository;
import dev.ledger.app.repo.TransactionRepository;
import dev.ledger.core.money.Money;
import dev.ledger.imports.Fixtures;
import java.time.YearMonth;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** SPEC §6.5: a budget per category per month, evaluated on write, exposed as an endpoint. */
class BudgetAlertIntegrationTest extends PostgresIntegrationTest {

  private static final YearMonth JANUARY = YearMonth.of(2026, 1);

  @Autowired private StatementImportAppService importer;
  @Autowired private BudgetService budgets;
  @Autowired private ReportService reports;
  @Autowired private BudgetRepository budgetRepository;
  @Autowired private BudgetAlertRepository alertRepository;
  @Autowired private StatementRepository statements;
  @Autowired private TransactionRepository transactions;

  @BeforeEach
  void clean() {
    alertRepository.deleteAllInBatch();
    budgetRepository.deleteAllInBatch();
    transactions.deleteAllInBatch();
    statements.deleteAllInBatch();
  }

  private void importJanuary() {
    importer.importFile(Fixtures.bytes("garanti-2026-01.pdf"), "garanti-2026-01.pdf", null);
  }

  @Test
  @DisplayName("importing a statement raises an alert for a category already over budget")
  void evaluatesOnImport() {
    budgets.setLimit("yemek", Money.tryLira("100.00"));

    importJanuary();

    List<BudgetAlertEntity> alerts = budgets.alerts(JANUARY);
    assertThat(alerts).hasSize(1);
    BudgetAlertEntity alert = alerts.get(0);
    assertThat(alert.getCategoryId()).isEqualTo("yemek");
    assertThat(alert.getBudget()).isEqualTo(Money.tryLira("100.00"));
    assertThat(alert.getSpent()).isEqualTo(reports.nominalSpend(JANUARY, "yemek"));
    assertThat(alert.getOverspend()).isEqualTo(alert.getSpent().minus(alert.getBudget()));
    assertThat(alert.getRaisedAt()).isNotNull();
  }

  @Test
  @DisplayName("a category under its limit raises nothing")
  void staysQuietUnderTheLimit() {
    importJanuary();
    budgets.setLimit("yemek", Money.tryLira("10000.00"));
    budgets.evaluate(reports.monthsWithSpending());

    assertThat(budgets.alerts(null)).isEmpty();
  }

  @Test
  @DisplayName("raising the limit above the spend clears the alert rather than leaving it standing")
  void clearsWhenBackUnderTheLimit() {
    importJanuary();
    budgets.setLimit("yemek", Money.tryLira("100.00"));
    budgets.evaluate(reports.monthsWithSpending());
    assertThat(budgets.alerts(JANUARY)).hasSize(1);

    budgets.setLimit("yemek", Money.tryLira("9999.00"));
    budgets.evaluate(reports.monthsWithSpending());

    assertThat(budgets.alerts(JANUARY)).isEmpty();
  }

  @Test
  @DisplayName("re-evaluating updates the standing alert instead of piling up duplicates")
  void keepsOneAlertPerCategoryPerMonth() {
    budgets.setLimit("yemek", Money.tryLira("100.00"));
    importJanuary();
    budgets.evaluate(reports.monthsWithSpending());
    int afterFirst = alertRepository.findAll().size();

    budgets.evaluate(reports.monthsWithSpending());
    budgets.evaluate(reports.monthsWithSpending());

    assertThat(alertRepository.findAll()).hasSize(afterFirst);
    assertThat(budgets.alerts(JANUARY)).hasSize(1);
  }

  @Test
  @DisplayName("alerts are per month; a second month over budget is its own alert")
  void alertsArePerMonth() {
    budgets.setLimit("yemek", Money.tryLira("100.00"));
    importJanuary();
    // The December days of the same statement are their own month.
    budgets.evaluate(reports.monthsWithSpending());

    assertThat(budgets.alerts(null))
        .extracting(BudgetAlertEntity::getMonth)
        .contains(JANUARY, YearMonth.of(2025, 12));
    assertThat(budgets.alerts(JANUARY)).hasSize(1);
  }

  @Test
  @DisplayName("a budget is compared against nominal spending, which is what left the account")
  void comparesNominalSpending() {
    importJanuary();
    budgets.setLimit("yemek", Money.tryLira("100.00"));
    budgets.evaluate(List.of(JANUARY));

    BudgetAlertEntity alert = budgets.alerts(JANUARY).get(0);
    // Not the real-terms figure, which for January 2026 is materially larger.
    assertThat(alert.getSpent())
        .isEqualTo(
            reports.monthly(JANUARY, null).categories().stream()
                .filter(line -> line.categoryId().equals("yemek"))
                .findFirst()
                .orElseThrow()
                .amount()
                .nominal());
  }

  @Test
  @DisplayName("a budget for a category that does not exist is refused")
  void refusesUnknownCategories() {
    assertThatThrownBy(() -> budgets.setLimit("does-not-exist", Money.tryLira("100.00")))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("no such category");
  }

  @Test
  @DisplayName("a non-positive budget is refused")
  void refusesNonPositiveBudgets() {
    assertThatThrownBy(() -> budgets.setLimit("yemek", Money.tryLira("0.00")))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> budgets.setLimit("yemek", Money.tryLira("-1.00")))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  @DisplayName("removing a budget that was never set is a 404, not a silent success")
  void refusesToRemoveAMissingBudget() {
    assertThatThrownBy(() -> budgets.remove("konut"))
        .isInstanceOf(java.util.NoSuchElementException.class);
  }
}
