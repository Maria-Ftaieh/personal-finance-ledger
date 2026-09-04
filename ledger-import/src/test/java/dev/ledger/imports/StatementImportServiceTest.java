package dev.ledger.imports;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ledger.core.model.BankCode;
import dev.ledger.core.model.ImportResult;
import dev.ledger.core.money.Money;
import dev.ledger.imports.fixtures.FixtureGenerator;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** SPEC §3.3: every outcome of an import is one of the four sealed cases, and each is reachable. */
class StatementImportServiceTest {

  private final StatementImportService service = new StatementImportService();

  @Test
  @DisplayName("detection order picks the right adapter for each bank")
  void routesToTheRightAdapter() {
    assertThat(parsed("garanti-2026-01.pdf").bank()).isEqualTo(BankCode.GARANTI_BBVA);
    assertThat(parsed("yapikredi-2026-01.pdf").bank()).isEqualTo(BankCode.YAPI_KREDI);
    assertThat(parsed("generic-export.csv").bank()).isEqualTo(BankCode.GENERIC_CSV);
  }

  @Test
  @DisplayName("an encrypted statement asks for a password instead of failing")
  void needsPassword() {
    ImportResult result =
        service.importFile(Fixtures.bytes("garanti-2026-01-protected.pdf"), "protected.pdf", null);

    assertThat(result).isInstanceOf(ImportResult.NeedsPassword.class);
    assertThat(((ImportResult.NeedsPassword) result).fileName()).isEqualTo("protected.pdf");
    // The result carries no password field at all, so it cannot leak into a log.
    assertThat(ImportResult.NeedsPassword.class.getRecordComponents()).hasSize(1);
  }

  @Test
  @DisplayName("a wrong password is still NeedsPassword, not a crash")
  void wrongPassword() {
    assertThat(service.importFile(Fixtures.bytes("garanti-2026-01-protected.pdf"), "p.pdf", "0000"))
        .isInstanceOf(ImportResult.NeedsPassword.class);
  }

  @Test
  @DisplayName("the right password decrypts and parses")
  void correctPasswordParses() {
    ImportResult result =
        service.importFile(
            Fixtures.bytes("garanti-2026-01-protected.pdf"),
            "protected.pdf",
            FixtureGenerator.FIXTURE_PASSWORD);

    assertThat(result).isInstanceOf(ImportResult.Parsed.class);
    assertThat(((ImportResult.Parsed) result).statement().transactions()).hasSize(10);
  }

  @Test
  @DisplayName("a scan is reported unreadable rather than parsed into invented rows")
  void scannedStatementIsUnreadable() {
    ImportResult result =
        service.importFile(Fixtures.bytes("scanned-statement.pdf"), "scan.pdf", null);

    assertThat(result).isInstanceOf(ImportResult.Unreadable.class);
    assertThat(((ImportResult.Unreadable) result).reason()).contains("scanned");
  }

  @Test
  @DisplayName("a readable PDF from an unknown bank is reported as unsupported")
  void unknownBankIsUnsupported() throws IOException {
    ImportResult result = service.importFile(plainPdf(), "unknown.pdf", null);

    assertThat(result).isInstanceOf(ImportResult.UnsupportedBank.class);
  }

  @Test
  @DisplayName("the file hash is recorded so a re-upload can be caught before parsing")
  void recordsTheFileHash() {
    byte[] content = Fixtures.bytes("garanti-2026-01.pdf");

    assertThat(parsed("garanti-2026-01.pdf").contentHash())
        .isEqualTo(StatementImportService.sha256(content))
        .hasSize(64);
  }

  @Test
  @DisplayName("the CSV fallback reads quoted fields, instalments and foreign amounts")
  void readsTheCsvFallback() {
    var statement = parsed("generic-export.csv");

    assertThat(statement.transactions()).hasSize(4);
    assertThat(statement.transactions().get(1).installment().total()).isEqualTo(8);
    assertThat(statement.transactions().get(2).originalAmount())
        .isEqualTo(Money.of("9.99", Money.EUR));
    // A comma inside a quoted description must not split the row.
    assertThat(statement.transactions().get(3).rawDescription())
        .isEqualTo("TRENDYOL SİPARİŞ, İADE");
    assertThat(statement.transactions().get(3).amount()).isEqualTo(Money.tryLira("-249.90"));
  }

  @Test
  @DisplayName("garbage is unreadable, not an exception on the caller's stack")
  void garbageIsUnreadable() {
    assertThat(
            service.importFile(
                "not a statement".getBytes(java.nio.charset.StandardCharsets.UTF_8), "x.csv", null))
        .isInstanceOf(ImportResult.Unreadable.class);
  }

  private dev.ledger.core.model.ParsedStatement parsed(String fixture) {
    ImportResult result = service.importFile(Fixtures.bytes(fixture), fixture, null);
    assertThat(result).isInstanceOf(ImportResult.Parsed.class);
    return ((ImportResult.Parsed) result).statement();
  }

  /** A PDF with plenty of text but no issuer any adapter knows. */
  private static byte[] plainPdf() throws IOException {
    try (PDDocument document = new PDDocument();
        ByteArrayOutputStream out = new ByteArrayOutputStream()) {
      PDPage page = new PDPage();
      document.addPage(page);
      try (PDPageContentStream content = new PDPageContentStream(document, page)) {
        content.beginText();
        content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 10);
        content.newLineAtOffset(40, 700);
        content.showText(
            "This document is a lease agreement and contains no card transactions ".repeat(4));
        content.endText();
      }
      document.save(out);
      return out.toByteArray();
    }
  }
}
