package dev.ledger.core.dedup;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ledger.core.TestData;
import dev.ledger.core.model.ParsedStatement;
import dev.ledger.core.model.Transaction;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * SPEC §3.4. The scenarios here are the ones the fixture pair of overlapping statements must
 * survive: same-day repeats, a refund, a differently truncated description, and an instalment line
 * printed on both documents.
 */
class DuplicateDetectorTest {

  private final DuplicateDetector detector = new DuplicateDetector();

  /** Total lines that survive the pass — nothing is deleted, so this is a count, not a deletion. */
  private static int survivors(DedupReport report, ParsedStatement... statements) {
    int total = 0;
    for (ParsedStatement statement : statements) {
      total += statement.size();
    }
    return total - report.size();
  }

  @Test
  @DisplayName("the same purchase printed on two overlapping statements is flagged once")
  void flagsTheOverlap() {
    ParsedStatement january =
        TestData.statement("16.12.2025", "15.01.2026")
            .line("05.01.2026", "SPOTIFY AB STOCKHOLM 112233445", "129.99")
            .build();
    ParsedStatement february =
        TestData.statement("01.01.2026", "31.01.2026")
            .line("05.01.2026", "SPOTIFY AB STOCKHOLM 998877665", "129.99")
            .build();

    DedupReport report = detector.detect(List.of(january, february));

    assertThat(report.size()).isEqualTo(1);
    DuplicateMatch match = report.matches().get(0);
    // The later statement's copy is the suspect; the earlier one stays as the original.
    assertThat(match.duplicateStatement()).isEqualTo(february.id());
    assertThat(match.originalStatement()).isEqualTo(january.id());
    assertThat(match.reason()).isEqualTo(MatchReason.DESCRIPTION_SIMILARITY);
    assertThat(match.similarity()).isEqualTo(1.0);
  }

  @Test
  @DisplayName("two identical purchases on the same day are legitimate and both survive")
  void keepsTwoIdenticalSameDayPurchases() {
    ParsedStatement january =
        TestData.statement("16.12.2025", "15.01.2026")
            .line("05.01.2026", "KAHVE DUNYASI KADIKOY", "85.00")
            .line("05.01.2026", "KAHVE DUNYASI KADIKOY", "85.00")
            .build();
    ParsedStatement february =
        TestData.statement("01.01.2026", "31.01.2026")
            .line("05.01.2026", "KAHVE DUNYASI KADIKOY", "85.00")
            .line("05.01.2026", "KAHVE DUNYASI KADIKOY", "85.00")
            .build();

    DedupReport report = detector.detect(List.of(january, february));

    // max(2, 2) = 2, not 1 and not 4.
    assertThat(report.size()).isEqualTo(2);
    assertThat(survivors(report, january, february)).isEqualTo(2);
  }

  @Test
  @DisplayName("the count rule is max(n, m), not n + m and not one")
  void keepsTheLargerOfTheTwoAccounts() {
    ParsedStatement january =
        TestData.statement("16.12.2025", "15.01.2026")
            .line("05.01.2026", "KAHVE DUNYASI KADIKOY", "85.00")
            .line("05.01.2026", "KAHVE DUNYASI KADIKOY", "85.00")
            .build();
    ParsedStatement february =
        TestData.statement("01.01.2026", "31.01.2026")
            .line("05.01.2026", "KAHVE DUNYASI KADIKOY", "85.00")
            .line("05.01.2026", "KAHVE DUNYASI KADIKOY", "85.00")
            .line("05.01.2026", "KAHVE DUNYASI KADIKOY", "85.00")
            .build();

    DedupReport report = detector.detect(List.of(january, february));

    assertThat(survivors(report, january, february)).isEqualTo(3);
  }

  @Test
  @DisplayName("three overlapping statements of one purchase still leave one")
  void collapsesAcrossThreeStatements() {
    ParsedStatement a =
        TestData.statement("16.12.2025", "15.01.2026")
            .line("10.01.2026", "GETIR MARKET", "64.50")
            .build();
    ParsedStatement b =
        TestData.statement("01.01.2026", "31.01.2026")
            .line("10.01.2026", "GETIR MARKET", "64.50")
            .build();
    ParsedStatement c =
        TestData.statement("05.01.2026", "05.02.2026")
            .line("10.01.2026", "GETIR MARKET", "64.50")
            .build();

    DedupReport report = detector.detect(List.of(a, b, c));

    assertThat(survivors(report, a, b, c)).isEqualTo(1);
  }

  @Test
  @DisplayName("a description truncated at a different column width is still recognised")
  void matchesDifferentlyTruncatedDescriptions() {
    ParsedStatement january =
        TestData.statement("16.12.2025", "15.01.2026")
            .line("07.01.2026", "MIGROS TICARET AS ISTANBUL", "342.75")
            .build();
    ParsedStatement february =
        TestData.statement("01.01.2026", "31.01.2026")
            .line("07.01.2026", "MIGROS TICARET A", "342.75")
            .build();

    DedupReport report = detector.detect(List.of(january, february));

    assertThat(report.size()).isEqualTo(1);
    assertThat(report.matches().get(0).reason()).isEqualTo(MatchReason.TRUNCATED_DESCRIPTION);
  }

  @Test
  @DisplayName("an instalment line on both statements matches once the marker is stripped")
  void matchesInstalmentLines() {
    ParsedStatement january =
        TestData.statement("16.12.2025", "15.01.2026")
            .installment("09.01.2026", "APPLE STORE ISTANBUL 3/8", "1250.00", 3, 8)
            .build();
    ParsedStatement february =
        TestData.statement("01.01.2026", "31.01.2026")
            .installment("09.01.2026", "APPLE STORE ISTANBUL 03/08", "1250.00", 3, 8)
            .build();

    DedupReport report = detector.detect(List.of(january, february));

    assertThat(report.size()).isEqualTo(1);
    assertThat(report.matches().get(0).similarity()).isEqualTo(1.0);
  }

  @Test
  @DisplayName("a refund is never merged with the charge it reverses")
  void neverMergesARefundWithItsCharge() {
    ParsedStatement january =
        TestData.statement("16.12.2025", "15.01.2026")
            .line("12.01.2026", "TRENDYOL SIPARIS", "249.90")
            .line("12.01.2026", "TRENDYOL SIPARIS IADE", "-249.90")
            .build();
    ParsedStatement february =
        TestData.statement("01.01.2026", "31.01.2026")
            .line("12.01.2026", "TRENDYOL SIPARIS", "249.90")
            .line("12.01.2026", "TRENDYOL SIPARIS IADE", "-249.90")
            .build();

    DedupReport report = detector.detect(List.of(january, february));

    // One charge and one refund survive: the pair nets to zero, which it must, and neither line
    // was mistaken for the other despite the near-identical description on the same day.
    assertThat(report.size()).isEqualTo(2);
    assertThat(survivors(report, january, february)).isEqualTo(2);

    List<Transaction> flagged =
        february.transactions().stream()
            .filter(t -> report.suspectedDuplicateIds().contains(t.id()))
            .toList();
    assertThat(flagged).hasSize(2);
  }

  @Test
  @DisplayName("deduplication never runs within a single statement")
  void neverDeduplicatesWithinOneStatement() {
    ParsedStatement january =
        TestData.statement("16.12.2025", "15.01.2026")
            .line("05.01.2026", "KAHVE DUNYASI KADIKOY", "85.00")
            .line("05.01.2026", "KAHVE DUNYASI KADIKOY", "85.00")
            .line("05.01.2026", "KAHVE DUNYASI KADIKOY", "85.00")
            .build();

    assertThat(detector.detect(List.of(january)).isEmpty()).isTrue();
  }

  @Test
  @DisplayName("statements whose periods do not overlap are never compared")
  void ignoresNonOverlappingStatements() {
    ParsedStatement january =
        TestData.statement("16.12.2025", "15.01.2026")
            .line("05.01.2026", "GETIR MARKET", "64.50")
            .build();
    // A late-posted line dated in January but belonging to a period that starts in February.
    ParsedStatement march =
        TestData.statement("16.02.2026", "15.03.2026")
            .line("05.01.2026", "GETIR MARKET", "64.50")
            .build();

    assertThat(detector.detect(List.of(january, march)).isEmpty()).isTrue();
  }

  @Test
  @DisplayName("an amount that differs by a kuruş is a different purchase")
  void amountsAreNeverFuzzyMatched() {
    ParsedStatement january =
        TestData.statement("16.12.2025", "15.01.2026")
            .line("05.01.2026", "GETIR MARKET", "64.50")
            .build();
    ParsedStatement february =
        TestData.statement("01.01.2026", "31.01.2026")
            .line("05.01.2026", "GETIR MARKET", "64.51")
            .build();

    assertThat(detector.detect(List.of(january, february)).isEmpty()).isTrue();
  }

  @Test
  @DisplayName("unrelated merchants sharing one token on the same day are not merged")
  void doesNotMergeUnrelatedMerchants() {
    ParsedStatement january =
        TestData.statement("16.12.2025", "15.01.2026")
            .line("05.01.2026", "AKBANK ATM", "100.00")
            .build();
    ParsedStatement february =
        TestData.statement("01.01.2026", "31.01.2026")
            .line("05.01.2026", "AKBANK KOMISYON", "100.00")
            .build();

    assertThat(detector.detect(List.of(january, february)).isEmpty()).isTrue();
  }

  @Test
  @DisplayName("the report identifies what to flag, and flags nothing itself")
  void reportsRatherThanDeletes() {
    ParsedStatement january =
        TestData.statement("16.12.2025", "15.01.2026")
            .line("05.01.2026", "GETIR MARKET", "64.50")
            .build();
    ParsedStatement february =
        TestData.statement("01.01.2026", "31.01.2026")
            .line("05.01.2026", "GETIR MARKET", "64.50")
            .build();

    DedupReport report = detector.detect(List.of(january, february));

    assertThat(february.transactions()).hasSize(1);
    assertThat(report.byDuplicateId()).containsOnlyKeys(february.transactions().get(0).id());
    assertThat(report.byDuplicateId().get(february.transactions().get(0).id()).original())
        .isEqualTo(january.transactions().get(0).id());
  }
}
