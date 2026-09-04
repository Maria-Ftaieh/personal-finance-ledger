package dev.ledger.app.service;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ledger.app.PostgresIntegrationTest;
import dev.ledger.app.config.CurrentUser;
import dev.ledger.app.domain.DuplicateStatus;
import dev.ledger.app.domain.TransactionEntity;
import dev.ledger.app.repo.StatementRepository;
import dev.ledger.app.repo.TransactionRepository;
import dev.ledger.app.service.StatementImportAppService.ImportOutcome;
import dev.ledger.core.model.BankCode;
import dev.ledger.core.money.Money;
import dev.ledger.imports.Fixtures;
import dev.ledger.imports.fixtures.FixtureGenerator;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** The upload path, end to end, against a real PostgreSQL. */
class StatementImportIntegrationTest extends PostgresIntegrationTest {

  @Autowired private StatementImportAppService importer;
  @Autowired private StatementRepository statements;
  @Autowired private TransactionRepository transactions;

  @BeforeEach
  void clean() {
    transactions.deleteAllInBatch();
    statements.deleteAllInBatch();
  }

  private ImportOutcome importFixture(String name) {
    return importer.importFile(Fixtures.bytes(name), name, null);
  }

  private TransactionEntity find(String fragment) {
    return transactions.findByUserId(CurrentUser.ID).stream()
        .filter(t -> t.getRawDescription().contains(fragment))
        .findFirst()
        .orElseThrow(() -> new AssertionError("no transaction matching " + fragment));
  }

  @Test
  @DisplayName("a statement is parsed, stored and categorised in one call")
  void importsAndCategorises() {
    ImportOutcome outcome = importFixture("garanti-2026-01.pdf");

    assertThat(outcome.status()).isEqualTo(ImportOutcome.Status.IMPORTED);
    assertThat(outcome.transactionsImported()).isEqualTo(10);
    assertThat(outcome.suspectedDuplicates()).isZero();

    assertThat(statements.findById(outcome.statementId()).orElseThrow())
        .satisfies(
            statement -> {
              assertThat(statement.getBank()).isEqualTo(BankCode.GARANTI_BBVA);
              assertThat(statement.getPeriodStart()).isEqualTo(LocalDate.of(2025, 12, 16));
              assertThat(statement.getPeriodEnd()).isEqualTo(LocalDate.of(2026, 1, 15));
              assertThat(statement.getContentHash()).hasSize(64);
              assertThat(statement.getImportedAt()).isNotNull();
            });

    assertThat(find("KAHVE").getCategoryId()).isEqualTo("yemek");
    assertThat(find("KAHVE").getSubcategoryId()).isEqualTo("yemek.kahve");
    assertThat(find("SHELL").getCategoryId()).isEqualTo("ulasim");
  }

  @Test
  @DisplayName("card fees go to a system category, not into uncategorised spending")
  void routesFeesToASystemCategory() {
    importFixture("garanti-2026-01.pdf");

    assertThat(find("KART ÜCRETİ").getCategoryId()).isEqualTo("finansal");
    assertThat(find("KART ÜCRETİ").getSubcategoryId()).isEqualTo("finansal.kart_ucreti");
  }

  @Test
  @DisplayName("money survives the round trip as an exact decimal, refunds and foreign amounts too")
  void storesMoneyExactly() {
    importFixture("garanti-2026-01.pdf");

    assertThat(find("SPOTIFY").getAmount()).isEqualTo(Money.tryLira("354.60"));
    assertThat(find("SPOTIFY").getOriginalAmount()).isEqualTo(Money.of("9.99", Money.EUR));
    assertThat(find("İADE").getAmount()).isEqualTo(Money.tryLira("-249.90"));
    assertThat(find("APPLE STORE").getInstallment().toString()).isEqualTo("3/8");
  }

  @Test
  @DisplayName("re-uploading the identical file is a no-op decided by the hash, before parsing")
  void reuploadingTheSameFileIsANoOp() {
    ImportOutcome first = importFixture("garanti-2026-01.pdf");
    ImportOutcome second = importFixture("garanti-2026-01.pdf");

    assertThat(second.status()).isEqualTo(ImportOutcome.Status.ALREADY_IMPORTED);
    assertThat(second.statementId()).isEqualTo(first.statementId());
    assertThat(transactions.count()).isEqualTo(10);
    assertThat(statements.count()).isEqualTo(1);
  }

  @Test
  @DisplayName("an overlapping statement has its repeated lines flagged, and nothing is deleted")
  void flagsDuplicatesAcrossOverlappingStatements() {
    importFixture("garanti-2026-01.pdf");
    ImportOutcome february = importFixture("garanti-2026-02.pdf");

    assertThat(february.transactionsImported()).isEqualTo(9);
    assertThat(february.suspectedDuplicates()).isEqualTo(7);
    // Both statements keep every row they were parsed with.
    assertThat(transactions.count()).isEqualTo(19);

    List<TransactionEntity> suspected =
        transactions.findByUserIdAndDuplicateStatusOrderByTransactionDateDesc(
            CurrentUser.ID, DuplicateStatus.SUSPECTED);
    assertThat(suspected).hasSize(7);
    assertThat(suspected).allSatisfy(row -> assertThat(row.getDuplicateOfId()).isNotNull());
    assertThat(suspected)
        .anySatisfy(
            row -> {
              assertThat(row.getRawDescription()).isEqualTo("MİGROS TİCARET A");
              assertThat(row.getDuplicateReason()).isEqualTo("TRUNCATED_DESCRIPTION");
            });
  }

  @Test
  @DisplayName("two identical same-day purchases both survive the overlap")
  void keepsBothOfTwoIdenticalPurchases() {
    importFixture("garanti-2026-01.pdf");
    importFixture("garanti-2026-02.pdf");

    long survivingCoffees =
        transactions.findByUserId(CurrentUser.ID).stream()
            .filter(t -> t.getRawDescription().startsWith("KAHVE"))
            .filter(t -> t.getDuplicateStatus() == DuplicateStatus.NONE)
            .count();

    assertThat(survivingCoffees).isEqualTo(2);
  }

  @Test
  @DisplayName("two identical rows in one file get distinct fingerprints rather than colliding")
  void fingerprintsDistinguishIdenticalRows() {
    importFixture("garanti-2026-01.pdf");

    List<String> coffeeFingerprints =
        transactions.findByUserId(CurrentUser.ID).stream()
            .filter(t -> t.getRawDescription().startsWith("KAHVE"))
            .map(TransactionEntity::getFingerprint)
            .toList();

    assertThat(coffeeFingerprints).hasSize(2).doesNotHaveDuplicates();
  }

  @Test
  @DisplayName("an encrypted file asks for a password, and opens with the right one")
  void handlesPasswordProtectedFiles() {
    assertThat(importFixture("garanti-2026-01-protected.pdf").status())
        .isEqualTo(ImportOutcome.Status.NEEDS_PASSWORD);
    assertThat(statements.count()).isZero();

    ImportOutcome opened =
        importer.importFile(
            Fixtures.bytes("garanti-2026-01-protected.pdf"),
            "protected.pdf",
            FixtureGenerator.FIXTURE_PASSWORD);

    assertThat(opened.status()).isEqualTo(ImportOutcome.Status.IMPORTED);
    assertThat(opened.transactionsImported()).isEqualTo(10);
  }

  @Test
  @DisplayName("a scan is reported unreadable and stores nothing")
  void storesNothingForAScan() {
    assertThat(importFixture("scanned-statement.pdf").status())
        .isEqualTo(ImportOutcome.Status.UNREADABLE);

    assertThat(statements.count()).isZero();
    assertThat(transactions.count()).isZero();
  }

  @Test
  @DisplayName("the CSV fallback lands in the same tables as a PDF import")
  void importsTheCsvFallback() {
    ImportOutcome outcome = importFixture("generic-export.csv");

    assertThat(outcome.status()).isEqualTo(ImportOutcome.Status.IMPORTED);
    assertThat(outcome.transactionsImported()).isEqualTo(4);
    assertThat(statements.findById(outcome.statementId()).orElseThrow().getBank())
        .isEqualTo(BankCode.GENERIC_CSV);
  }
}
