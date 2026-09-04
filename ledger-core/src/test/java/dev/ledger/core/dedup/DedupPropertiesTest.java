package dev.ledger.core.dedup;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ledger.core.TestData;
import dev.ledger.core.model.ParsedStatement;
import dev.ledger.core.model.TransactionId;
import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

/**
 * SPEC §7.2, the two dedup invariants. Generated statements all cover the same January period so
 * every pair overlaps and the algorithm is actually exercised.
 */
class DedupPropertiesTest {

  private final DuplicateDetector detector = new DuplicateDetector();

  @Property(tries = 300)
  void deduplicatingAStatementAgainstItselfIsANoOp(@ForAll("statement") ParsedStatement statement) {
    assertThat(detector.detect(List.of(statement)).isEmpty()).isTrue();
    assertThat(detector.detect(List.of(statement, statement)).isEmpty()).isTrue();
  }

  @Property(tries = 300)
  void theOrderStatementsAreImportedInDoesNotMatter(
      @ForAll("statement") ParsedStatement a, @ForAll("statement") ParsedStatement b) {
    assertThat(unorderedPairs(detector.detect(List.of(a, b))))
        .isEqualTo(unorderedPairs(detector.detect(List.of(b, a))));
  }

  @Property(tries = 300)
  void neverFlagsMoreThanTheSmallerStatementHolds(
      @ForAll("statement") ParsedStatement a, @ForAll("statement") ParsedStatement b) {
    // Each bucket contributes min(n, m) matches, so the total cannot exceed the smaller statement.
    DedupReport report = detector.detect(List.of(a, b));

    assertThat(report.size()).isLessThanOrEqualTo(Math.min(a.size(), b.size()));
    assertThat(report.matches().stream().map(DuplicateMatch::duplicate).toList())
        .doesNotHaveDuplicates();
  }

  private static Set<Set<TransactionId>> unorderedPairs(DedupReport report) {
    return report.matches().stream()
        .map(m -> (Set<TransactionId>) new HashSet<>(List.of(m.duplicate(), m.original())))
        .collect(Collectors.toSet());
  }

  private record Line(int day, String description, int cents) {}

  @Provide
  Arbitrary<ParsedStatement> statement() {
    Arbitrary<Line> lines =
        Combinators.combine(
                Arbitraries.integers().between(1, 28),
                Arbitraries.of(
                    "KAHVE DUNYASI KADIKOY",
                    "MIGROS TICARET AS ISTANBUL",
                    "MIGROS TICARET A",
                    "GETIR MARKET",
                    "SPOTIFY AB STOCKHOLM",
                    "AKBANK ATM",
                    "AKBANK KOMISYON",
                    "TRENDYOL SIPARIS"),
                Arbitraries.of(8500, -8500, 34275, 6450, 12999, -12999, 10000))
            .as(Line::new);

    return lines.list().ofMaxSize(8).map(DedupPropertiesTest::toStatement);
  }

  private static ParsedStatement toStatement(List<Line> lines) {
    TestData.StatementBuilder builder = TestData.statement("01.01.2026", "31.01.2026");
    for (Line line : lines) {
      builder.line(
          String.format(Locale.ROOT, "%02d.01.2026", line.day()),
          line.description(),
          BigDecimal.valueOf(line.cents(), 2).toPlainString());
    }
    return builder.build();
  }
}
