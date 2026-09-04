package dev.ledger.app.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.ledger.app.PostgresIntegrationTest;
import dev.ledger.app.config.CurrentUser;
import dev.ledger.app.domain.DuplicateStatus;
import dev.ledger.app.domain.RuleEntity;
import dev.ledger.app.domain.TransactionEntity;
import dev.ledger.app.repo.RuleRepository;
import dev.ledger.app.repo.StatementRepository;
import dev.ledger.app.repo.TransactionRepository;
import dev.ledger.app.service.RuleService.RuleDraft;
import dev.ledger.core.rules.MatchType;
import dev.ledger.imports.Fixtures;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** Rules, manual overrides, the preview, and the duplicate review queue. */
class CategorisationIntegrationTest extends PostgresIntegrationTest {

  @Autowired private StatementImportAppService importer;
  @Autowired private CategorisationService categorisation;
  @Autowired private DuplicateService duplicates;
  @Autowired private RuleService rules;
  @Autowired private RuleRepository ruleRepository;
  @Autowired private StatementRepository statements;
  @Autowired private TransactionRepository transactions;

  @BeforeEach
  void seedOneStatement() {
    transactions.deleteAllInBatch();
    statements.deleteAllInBatch();
    ruleRepository.deleteAll(
        ruleRepository.findByUserIdOrderByPriorityAscIdAsc(CurrentUser.ID).stream()
            .filter(RuleEntity::isUserDefined)
            .toList());
    importer.importFile(Fixtures.bytes("garanti-2026-01.pdf"), "garanti-2026-01.pdf", null);
  }

  private TransactionEntity find(String fragment) {
    return transactions.findByUserId(CurrentUser.ID).stream()
        .filter(t -> t.getRawDescription().contains(fragment))
        .findFirst()
        .orElseThrow(() -> new AssertionError("no transaction matching " + fragment));
  }

  @Test
  @DisplayName("a user rule at priority 1 outranks every seeded rule")
  void userRulesCanOutrankSystemRules() {
    assertThat(find("KAHVE").getCategoryId()).isEqualTo("yemek");

    // Written the way a user would type it: Turkish characters, lower case. The engine folds
    // the pattern the same way it folds the description, so it still matches.
    rules.create(new RuleDraft(1, MatchType.CONTAINS, "kahve dünyası", "konut", "konut.kira"));
    categorisation.reevaluateAll();

    assertThat(find("KAHVE").getCategoryId()).isEqualTo("konut");
  }

  @Test
  @DisplayName("a manual assignment survives a bulk re-evaluation")
  void manualAssignmentSurvivesReevaluation() {
    TransactionEntity shell = find("SHELL");
    categorisation.assignManually(shell.getId(), "eglence", "eglence.etkinlik");

    rules.create(new RuleDraft(1, MatchType.CONTAINS, "SHELL", "konut", "konut.kira"));
    categorisation.reevaluateAll();

    TransactionEntity after = transactions.findById(shell.getId()).orElseThrow();
    assertThat(after.getCategoryId()).isEqualTo("eglence");
    assertThat(after.isCategoryOverride()).isTrue();
  }

  @Test
  @DisplayName("clearing the override hands the transaction back to the rules")
  void clearingTheOverrideRestoresTheRuleVerdict() {
    TransactionEntity shell = find("SHELL");
    categorisation.assignManually(shell.getId(), "eglence", "eglence.etkinlik");

    TransactionEntity restored = categorisation.clearManualAssignment(shell.getId());

    assertThat(restored.isCategoryOverride()).isFalse();
    assertThat(restored.getCategoryId()).isEqualTo("ulasim");
  }

  @Test
  @DisplayName("the preview counts what a re-evaluation would move, and moves nothing")
  void previewMatchesTheCommit() {
    categorisation.assignManually(find("KAHVE").getId(), "eglence", "eglence.etkinlik");
    rules.create(new RuleDraft(1, MatchType.CONTAINS, "KAHVE DUNYASI", "konut", "konut.kira"));

    CategorisationService.Preview preview = categorisation.preview();

    assertThat(preview.examined()).isEqualTo(10);
    assertThat(preview.heldByOverride()).isEqualTo(1);
    // One of the two coffees is held by the override, so only the other one moves.
    assertThat(preview.wouldChange()).isEqualTo(1);
    // Asking is not doing.
    assertThat(find("KAHVE").getCategoryId()).isNotEqualTo("konut");

    assertThat(categorisation.reevaluateAll()).isEqualTo(preview.wouldChange());
  }

  @Test
  @DisplayName("nothing a rule cannot classify is lost; it lands in the system fallback")
  void unmatchedTransactionsFallBack() {
    assertThat(find("APPLE STORE").getCategoryId())
        .isEqualTo(CategorisationService.UNCATEGORISED_CATEGORY);
    assertThat(find("APPLE STORE").getSubcategoryId())
        .isEqualTo(CategorisationService.UNCATEGORISED_SUBCATEGORY);
  }

  @Test
  @DisplayName("a regex that could backtrack catastrophically never reaches the database")
  void refusesDangerousRegexes() {
    assertThatThrownBy(
            () -> rules.create(new RuleDraft(1, MatchType.REGEX, "(a+)+b", "yemek", "yemek.kahve")))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("backtrack");

    assertThat(ruleRepository.findByUserIdOrderByPriorityAscIdAsc(CurrentUser.ID))
        .noneMatch(RuleEntity::isUserDefined);
  }

  @Test
  @DisplayName("a rule pointing at a category that does not exist is refused")
  void refusesUnknownCategories() {
    assertThatThrownBy(
            () -> rules.create(new RuleDraft(1, MatchType.CONTAINS, "X", "does-not-exist", null)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("no such category");
  }

  @Test
  @DisplayName("confirming a duplicate takes it out of the totals but leaves the row in place")
  void confirmingADuplicateKeepsTheRow() {
    importer.importFile(Fixtures.bytes("garanti-2026-02.pdf"), "garanti-2026-02.pdf", null);
    List<TransactionEntity> queue = duplicates.reviewQueue();
    assertThat(queue).hasSize(7);

    TransactionEntity confirmed = duplicates.confirm(queue.get(0).getId());
    TransactionEntity rejected = duplicates.reject(queue.get(1).getId());

    assertThat(confirmed.getDuplicateStatus()).isEqualTo(DuplicateStatus.CONFIRMED);
    assertThat(rejected.getDuplicateStatus()).isEqualTo(DuplicateStatus.REJECTED);
    assertThat(duplicates.reviewQueue()).hasSize(5);
    assertThat(transactions.count()).isEqualTo(19);

    // The default listing hides a confirmed duplicate and keeps a rejected one.
    List<TransactionEntity> listed = transactions.search(CurrentUser.ID, null, null, null, false);
    assertThat(listed).extracting(TransactionEntity::getId).doesNotContain(confirmed.getId());
    assertThat(listed).extracting(TransactionEntity::getId).contains(rejected.getId());
  }

  @Test
  @DisplayName("a decision the user has already made is never reopened by a later import")
  void doesNotReopenSettledDuplicates() {
    importer.importFile(Fixtures.bytes("garanti-2026-02.pdf"), "garanti-2026-02.pdf", null);
    TransactionEntity rejected = duplicates.reject(duplicates.reviewQueue().get(0).getId());

    // A third overlapping statement runs the pass again over the same rows.
    importer.importFile(Fixtures.bytes("generic-export.csv"), "generic-export.csv", null);

    assertThat(transactions.findById(rejected.getId()).orElseThrow().getDuplicateStatus())
        .isEqualTo(DuplicateStatus.REJECTED);
  }

  @Test
  @DisplayName("only a transaction awaiting review can be confirmed")
  void refusesToConfirmSomethingNotInTheQueue() {
    assertThatThrownBy(() -> duplicates.confirm(find("KART ÜCRETİ").getId()))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("not awaiting review");
  }
}
