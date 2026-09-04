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
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class GarantiBbvaImporterTest {

  private static ParsedStatement statement;

  @BeforeAll
  static void parseFixture() {
    ImportResult result =
        new StatementImportService()
            .importFile(Fixtures.bytes("garanti-2026-01.pdf"), "garanti-2026-01.pdf", null);

    assertThat(result).isInstanceOf(ImportResult.Parsed.class);
    statement = ((ImportResult.Parsed) result).statement();
  }

  private static Transaction find(String descriptionFragment) {
    return statement.transactions().stream()
        .filter(t -> t.rawDescription().contains(descriptionFragment))
        .findFirst()
        .orElseThrow(() -> new AssertionError("no transaction matching " + descriptionFragment));
  }

  @Test
  @DisplayName("the issuer is detected and every row of the table is read")
  void readsTheWholeTable() {
    assertThat(statement.bank()).isEqualTo(BankCode.GARANTI_BBVA);
    assertThat(statement.transactions()).hasSize(10);
    assertThat(statement.period().start()).isEqualTo(LocalDate.of(2025, 12, 16));
    assertThat(statement.period().end()).isEqualTo(LocalDate.of(2026, 1, 15));
  }

  @Test
  @DisplayName("the raw description survives extraction, Turkish characters and all")
  void preservesTurkishCharacters() {
    assertThat(find("MİGROS").rawDescription()).isEqualTo("MİGROS TİCARET AŞ İSTANBUL");
    assertThat(find("MİGROS").normalisedDescription()).isEqualTo("MIGROS TICARET AS ISTANBUL");
    assertThat(find("KAHVE").rawDescription()).isEqualTo("KAHVE DÜNYASI KADIKÖY");
  }

  @Test
  @DisplayName("columns are read positionally, so the amount is never confused with another column")
  void readsAmountsFromTheAmountColumn() {
    assertThat(find("MİGROS").amount()).isEqualTo(Money.tryLira("342.75"));
    assertThat(find("SHELL").amount()).isEqualTo(Money.tryLira("1480.00"));
    assertThat(find("KART ÜCRETİ").amount()).isEqualTo(Money.tryLira("750.00"));
  }

  @Test
  @DisplayName("a trailing minus is a refund, and stays negative")
  void readsTrailingMinusRefunds() {
    Transaction refund = find("İADE");

    assertThat(refund.amount()).isEqualTo(Money.tryLira("-249.90"));
    assertThat(refund.isRefund()).isTrue();
    assertThat(find("TRENDYOL SİPARİŞ").amount()).isEqualTo(Money.tryLira("249.90"));
  }

  @Test
  @DisplayName("a foreign currency purchase keeps both amounts")
  void readsForeignCurrency() {
    Transaction spotify = find("SPOTIFY");

    assertThat(spotify.amount()).isEqualTo(Money.tryLira("354.60"));
    assertThat(spotify.originalAmount()).isEqualTo(Money.of("9.99", Money.EUR));
    assertThat(spotify.isForeignCurrency()).isTrue();
  }

  @Test
  @DisplayName("an instalment marker is structured, and the row amount is this month's charge")
  void readsInstalments() {
    Transaction apple = find("APPLE STORE");

    assertThat(apple.installment()).isEqualTo(new Installment(3, 8));
    assertThat(apple.amount()).isEqualTo(Money.tryLira("1250.00"));
  }

  @Test
  @DisplayName("the posting date comes from the valör column, not from the transaction date")
  void readsBothDates() {
    Transaction apple = find("APPLE STORE");

    assertThat(apple.transactionDate()).isEqualTo(LocalDate.of(2026, 1, 9));
    assertThat(apple.postingDate()).isEqualTo(LocalDate.of(2026, 1, 10));
  }

  @Test
  @DisplayName("two identical same-day purchases are both read, not collapsed at import")
  void keepsSameDayRepeats() {
    List<Transaction> coffees =
        statement.transactions().stream()
            .filter(t -> t.rawDescription().startsWith("KAHVE"))
            .toList();

    assertThat(coffees).hasSize(2);
  }
}
