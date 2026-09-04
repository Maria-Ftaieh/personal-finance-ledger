package dev.ledger.core.text;

import static org.assertj.core.api.Assertions.assertThat;

import java.text.Normalizer;
import java.util.Locale;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** SPEC §3.2. The dotted/dotless I, and the folding split it forces. */
class TurkishTextTest {

  private final Locale original = Locale.getDefault();

  @AfterEach
  void restoreLocale() {
    Locale.setDefault(original);
  }

  @Test
  @DisplayName("the bug: the default locale changes what toLowerCase() returns")
  void defaultLocaleCaseFoldingIsUnstable() {
    Locale.setDefault(Locale.forLanguageTag("en-US"));
    String english = "TITLE".toLowerCase();

    Locale.setDefault(TurkishText.TURKISH);
    String turkish = "TITLE".toLowerCase();

    assertThat(english).isEqualTo("title");
    assertThat(turkish).isEqualTo("tıtle"); // dotless ı — silently breaks every string comparison
    assertThat(turkish).isNotEqualTo(english);
  }

  @Test
  @DisplayName("matching folds with Locale.ROOT and is unaffected by the default locale")
  void matchingFoldingIsStable() {
    Locale.setDefault(TurkishText.TURKISH);

    assertThat(TurkishText.lowerForMatching("TITLE")).isEqualTo("title");
    assertThat(TurkishText.upperForMatching("istanbul")).isEqualTo("ISTANBUL");
    assertThat(TurkishText.upperForMatching("İSTANBUL")).isEqualTo("İSTANBUL");
  }

  @Test
  @DisplayName("display folding honours Turkish casing")
  void displayFoldingIsTurkish() {
    Locale.setDefault(Locale.forLanguageTag("en-US"));

    assertThat(TurkishText.upperForDisplay("istanbul")).isEqualTo("İSTANBUL");
    assertThat(TurkishText.lowerForDisplay("IĞDIR")).isEqualTo("ığdır");
  }

  @ParameterizedTest(name = "{0} folds to {1}")
  @CsvSource({
    "İSTANBUL, ISTANBUL",
    "Kahve Dünyası, Kahve Dunyasi",
    "ÇİĞDEM, CIGDEM",
    "ŞİŞLİ, SISLI",
    "AĞUSTOS, AGUSTOS",
    "GÖZTEPE, GOZTEPE",
    "MIGROS, MIGROS"
  })
  @DisplayName("ASCII folding covers every Turkish-specific letter")
  void foldsToAscii(String input, String expected) {
    assertThat(TurkishText.foldToAscii(input)).isEqualTo(expected);
  }

  @Test
  @DisplayName("decomposed text from a PDF folds the same as composed text")
  void decomposedTextIsNormalised() {
    String composed = "ŞİŞLİ";
    String decomposed = Normalizer.normalize(composed, Normalizer.Form.NFD);

    assertThat(decomposed).isNotEqualTo(composed);
    assertThat(TurkishText.normalise(decomposed)).isEqualTo(composed);
    assertThat(TurkishText.foldToAscii(TurkishText.normalise(decomposed))).isEqualTo("SISLI");
  }
}
