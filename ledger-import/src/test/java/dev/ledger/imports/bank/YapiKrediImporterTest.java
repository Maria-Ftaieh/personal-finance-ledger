package dev.ledger.imports.bank;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ledger.core.model.BankCode;
import dev.ledger.core.model.ImportResult;
import dev.ledger.core.model.Installment;
import dev.ledger.core.model.ParsedStatement;
import dev.ledger.core.model.Transaction;
import dev.ledger.core.money.Money;
import dev.ledger.imports.Fixtures;
import dev.ledger.imports.StatementImportService;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The second adapter exists to prove the abstraction is real: a different layout, read the same
 * way.
 */
class YapiKrediImporterTest {

  private static ParsedStatement statement;

  @BeforeAll
  static void parseFixture() {
    ImportResult result =
        new StatementImportService()
            .importFile(Fixtures.bytes("yapikredi-2026-01.pdf"), "yapikredi-2026-01.pdf", null);

    assertThat(result).isInstanceOf(ImportResult.Parsed.class);
    statement = ((ImportResult.Parsed) result).statement();
  }

  private static Transaction find(String fragment) {
    return statement.transactions().stream()
        .filter(t -> t.rawDescription().contains(fragment))
        .findFirst()
        .orElseThrow(() -> new AssertionError("no transaction matching " + fragment));
  }

  @Test
  @DisplayName("a three-column layout with Turkish month abbreviations parses")
  void readsTheWholeTable() {
    assertThat(statement.bank()).isEqualTo(BankCode.YAPI_KREDI);
    assertThat(statement.transactions()).hasSize(5);
    assertThat(statement.period().start()).isEqualTo(LocalDate.of(2026, 1, 1));
    assertThat(statement.period().end()).isEqualTo(LocalDate.of(2026, 1, 31));
    assertThat(find("BİM").transactionDate()).isEqualTo(LocalDate.of(2026, 1, 3));
  }

  @Test
  @DisplayName("this bank prints refunds with a leading minus, and it still reads as negative")
  void readsLeadingMinusRefunds() {
    assertThat(find("İADE").amount()).isEqualTo(Money.tryLira("-229.99"));
  }

  @Test
  @DisplayName("with no valör column the posting date falls back to the transaction date")
  void fallsBackToOneDate() {
    Transaction eczane = find("ECZANE");

    assertThat(eczane.transactionDate()).isEqualTo(LocalDate.of(2026, 1, 15));
    assertThat(eczane.postingDate()).isEqualTo(eczane.transactionDate());
  }

  @Test
  @DisplayName("instalments and reference numbers are handled the same as on the other bank")
  void readsInstalmentsAndReferences() {
    assertThat(find("APPLE STORE").installment()).isEqualTo(new Installment(3, 8));
    assertThat(find("NETFLIX COM 9").rawDescription()).isEqualTo("NETFLIX COM 998877665");
    assertThat(find("NETFLIX COM 9").normalisedDescription()).isEqualTo("NETFLIX COM");
  }

  @Test
  @DisplayName("the Garanti adapter does not claim a Yapı Kredi document")
  void detectionIsSpecific() {
    assertThat(statement.bank()).isNotEqualTo(BankCode.GARANTI_BBVA);
  }
}
