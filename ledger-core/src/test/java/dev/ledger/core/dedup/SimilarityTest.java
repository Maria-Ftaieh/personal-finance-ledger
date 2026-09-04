package dev.ledger.core.dedup;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class SimilarityTest {

  @ParameterizedTest(name = "jaccard([{0}], [{1}]) = {2}")
  @CsvSource(
      delimiter = ';',
      value = {
        "MIGROS TICARET; MIGROS TICARET; 1.0",
        "MIGROS TICARET; MIGROS TICARET ISTANBUL; 0.6666666666666666",
        "AKBANK ATM; AKBANK KOMISYON; 0.3333333333333333",
        "SPOTIFY AB; NETFLIX COM; 0.0",
        "; ; 1.0"
      })
  @DisplayName("token-set Jaccard is blind to token order and sensitive to token drift")
  void jaccard(String left, String right, double expected) {
    String a = left == null ? "" : left;
    String b = right == null ? "" : right;

    assertThat(Similarity.tokenSetJaccard(a, b)).isEqualTo(expected);
    assertThat(Similarity.tokenSetJaccard(a, b)).isEqualTo(Similarity.tokenSetJaccard(b, a));
  }

  @Test
  @DisplayName("reordered tokens are identical to a set measure but not to a character measure")
  void jaccardIgnoresOrder() {
    assertThat(Similarity.tokenSetJaccard("ISTANBUL MIGROS", "MIGROS ISTANBUL")).isEqualTo(1.0);
    assertThat(Similarity.normalisedLevenshtein("ISTANBUL MIGROS", "MIGROS ISTANBUL"))
        .isLessThan(1.0);
  }

  @Test
  @DisplayName("normalised Levenshtein scores 1.0 only for identical strings")
  void levenshtein() {
    assertThat(Similarity.normalisedLevenshtein("GETIR", "GETIR")).isEqualTo(1.0);
    assertThat(Similarity.normalisedLevenshtein("GETIR", "GETIT")).isEqualTo(0.8);
    assertThat(Similarity.normalisedLevenshtein("", "")).isEqualTo(1.0);
    assertThat(Similarity.levenshtein("KITTEN", "SITTING")).isEqualTo(3);
  }

  @Test
  @DisplayName("truncation is recognised where Jaccard cannot see it")
  void truncation() {
    String full = "MIGROS TICARET AS ISTANBUL";
    String cut = "MIGROS TICARET A";

    assertThat(Similarity.tokenSetJaccard(cut, full))
        .isLessThan(DuplicateDetector.JACCARD_THRESHOLD);
    assertThat(Similarity.isTruncationOf(cut, full)).isTrue();
  }

  @Test
  @DisplayName("a short or divergent prefix is not treated as truncation")
  void truncationIsNarrow() {
    assertThat(Similarity.isTruncationOf("AKB", "AKBANK ATM ISTANBUL")).isFalse();
    assertThat(Similarity.isTruncationOf("MIGROS AS", "MIGROS TICARET AS")).isFalse();
    assertThat(Similarity.isTruncationOf("A B", "A B C D")).isFalse(); // under MIN_PREFIX_CHARS
  }
}
