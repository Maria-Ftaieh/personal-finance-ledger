package dev.ledger.core.text;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class DescriptionNormalizerTest {

  @ParameterizedTest(name = "[{0}] -> [{1}]")
  @CsvSource(
      delimiter = ';',
      value = {
        "KAHVE DÜNYASI İSTANBUL      ; KAHVE DUNYASI ISTANBUL",
        "  Migros   Ticaret A.S.     ; MIGROS TICARET A S",
        "SPOTIFY AB 3/8              ; SPOTIFY AB",
        "TRENDYOL 12/12              ; TRENDYOL",
        "SHELL AKARYAKIT 000123456789; SHELL AKARYAKIT",
        "OPET *4587221               ; OPET",
        "GETIR 20/01/2026            ; GETIR 20 01 2026",
        "BIM BIRLESIK MAGAZALAR 12345; BIM BIRLESIK MAGAZALAR 12345"
      })
  @DisplayName("derives a stable matching form")
  void normalises(String raw, String expected) {
    assertThat(DescriptionNormalizer.normalise(raw)).isEqualTo(expected.trim());
  }

  @Test
  @DisplayName("stacked reference numbers are all stripped")
  void stripsStackedReferences() {
    assertThat(DescriptionNormalizer.normalise("A101 KADIKOY 998877665 000112233"))
        .isEqualTo("A101 KADIKOY");
  }

  @Test
  @DisplayName("a short number is a branch code, not a reference, and is kept")
  void keepsShortNumbers() {
    assertThat(DescriptionNormalizer.normalise("A101 SUBE 402")).isEqualTo("A101 SUBE 402");
  }

  @Test
  @DisplayName("null and blank descriptions normalise to empty rather than exploding")
  void handlesEmptyInput() {
    assertThat(DescriptionNormalizer.normalise(null)).isEmpty();
    assertThat(DescriptionNormalizer.normalise("   ")).isEmpty();
  }
}
