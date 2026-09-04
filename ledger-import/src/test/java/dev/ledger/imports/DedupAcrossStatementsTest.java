package dev.ledger.imports;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ledger.core.dedup.DedupReport;
import dev.ledger.core.dedup.DuplicateDetector;
import dev.ledger.core.dedup.MatchReason;
import dev.ledger.core.model.ImportResult;
import dev.ledger.core.model.ParsedStatement;
import dev.ledger.core.model.Transaction;
import dev.ledger.core.money.Money;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * SPEC §3.4, end to end: two overlapping statements straight out of the PDF parser, through the
 * dedup pass. This is the case the whole design exists for, so it is worth running against real
 * extracted text rather than hand-built transactions.
 */
class DedupAcrossStatementsTest {

  private static ParsedStatement january;
  private static ParsedStatement february;
  private static DedupReport report;

  @BeforeAll
  static void importBoth() {
    january = parse("garanti-2026-01.pdf");
    february = parse("garanti-2026-02.pdf");
    report = new DuplicateDetector().detect(List.of(january, february));
  }

  private static ParsedStatement parse(String fixture) {
    ImportResult result =
        new StatementImportService().importFile(Fixtures.bytes(fixture), fixture, null);
    return ((ImportResult.Parsed) result).statement();
  }

  private static Transaction from(ParsedStatement statement, String fragment) {
    return statement.transactions().stream()
        .filter(t -> t.rawDescription().contains(fragment))
        .findFirst()
        .orElseThrow(() -> new AssertionError("no transaction matching " + fragment));
  }

  @Test
  @DisplayName("only the seven lines in the overlapping window are flagged")
  void flagsExactlyTheOverlap() {
    // 1–15 January is covered by both statements: two coffees, Spotify, Migros, the Apple
    // instalment, the Trendyol charge and its refund.
    assertThat(report.size()).isEqualTo(7);

    Set<java.util.UUID> flagged =
        report.suspectedDuplicateIds().stream()
            .map(id -> id.value())
            .collect(java.util.stream.Collectors.toSet());
    assertThat(
            february.transactions().stream().filter(t -> flagged.contains(t.id().value())).count())
        .isEqualTo(7);
    // Nothing from the earlier statement is ever the suspect.
    assertThat(
            january.transactions().stream().filter(t -> flagged.contains(t.id().value())).count())
        .isZero();
  }

  @Test
  @DisplayName("both coffees survive: two identical same-day purchases are not one purchase")
  void keepsBothCoffees() {
    long survivingCoffees =
        List.of(january, february).stream()
            .flatMap(s -> s.transactions().stream())
            .filter(t -> t.rawDescription().startsWith("KAHVE"))
            .filter(t -> !report.suspectedDuplicateIds().contains(t.id()))
            .count();

    assertThat(survivingCoffees).isEqualTo(2);
  }

  @Test
  @DisplayName("the differently truncated merchant name is matched as a truncation")
  void matchesTheTruncatedDescription() {
    var match = report.byDuplicateId().get(from(february, "MİGROS TİCARET A").id());

    assertThat(match).isNotNull();
    assertThat(match.reason()).isEqualTo(MatchReason.TRUNCATED_DESCRIPTION);
    assertThat(match.original()).isEqualTo(from(january, "MİGROS").id());
  }

  @Test
  @DisplayName("the instalment line matches despite 3/8 and 03/08 being written differently")
  void matchesTheInstalmentLine() {
    assertThat(report.byDuplicateId()).containsKey(from(february, "APPLE STORE").id());
  }

  @Test
  @DisplayName("the refund is matched to the other statement's refund, never to the charge")
  void matchesRefundToRefund() {
    var match = report.byDuplicateId().get(from(february, "SİPARİŞ İADE").id());

    assertThat(match.original()).isEqualTo(from(january, "SİPARİŞ İADE").id());
    assertThat(from(january, "SİPARİŞ İADE").amount()).isEqualTo(Money.tryLira("-249.90"));
  }

  @Test
  @DisplayName("lines outside the overlap are untouched in both directions")
  void leavesNonOverlappingLinesAlone() {
    assertThat(report.byDuplicateId()).doesNotContainKey(from(february, "GETİR").id());
    assertThat(report.byDuplicateId()).doesNotContainKey(from(february, "THY").id());
    assertThat(report.byDuplicateId()).doesNotContainKey(from(january, "A101").id());
    assertThat(report.byDuplicateId()).doesNotContainKey(from(january, "KART ÜCRETİ").id());
  }

  @Test
  @DisplayName("nothing is deleted; the statements still hold every row they were parsed with")
  void deletesNothing() {
    assertThat(january.transactions()).hasSize(10);
    assertThat(february.transactions()).hasSize(9);
  }
}
