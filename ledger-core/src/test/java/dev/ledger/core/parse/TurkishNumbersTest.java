package dev.ledger.core.parse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.Locale;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class TurkishNumbersTest {

  private final Locale original = Locale.getDefault();

  @AfterEach
  void restoreLocale() {
    Locale.setDefault(original);
  }

  @ParameterizedTest(name = "[{0}] -> {1}")
  @CsvSource({
    "'1.234,56', 1234.56",
    "'0,00', 0.00",
    "'12,50', 12.50",
    "'1.000.000,01', 1000000.01",
    "'249,90-', -249.90",
    "'-249,90', -249.90",
    "'+18,00', 18.00",
    "'1.234,56 TL', 1234.56",
    "'₺1.234,56', 1234.56",
    "'9,99 EUR', 9.99",
    "'1.234', 1234",
    "'123', 123"
  })
  @DisplayName("reads amounts the way a Turkish statement prints them")
  void parsesTurkishAmounts(String raw, BigDecimal expected) {
    assertThat(TurkishNumbers.parseAmount(raw)).isEqualByComparingTo(expected);
  }

  @Test
  @DisplayName("the trap: the naive parsers are wrong by a factor of a thousand, or throw")
  void naiveParsingIsWrong() {
    assertThat(new BigDecimal("1.234")).isEqualByComparingTo("1.234");
    assertThat(TurkishNumbers.parseAmount("1.234")).isEqualByComparingTo("1234");

    assertThatThrownBy(() -> new BigDecimal("1.234,56")).isInstanceOf(NumberFormatException.class);
    assertThat(TurkishNumbers.parseAmount("1.234,56")).isEqualByComparingTo("1234.56");
  }

  @ParameterizedTest
  @ValueSource(
      strings = {"", "  ", "abc", "1,2,3", "1.23.4,5,6", "12,34,56", "-", "1.234,56 extra"})
  @DisplayName("a partially recognised amount fails loudly instead of truncating")
  void rejectsMalformedAmounts(String raw) {
    assertThatThrownBy(() -> TurkishNumbers.parseAmount(raw))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  @DisplayName("parsing does not depend on the JVM default locale")
  void independentOfDefaultLocale() {
    Locale.setDefault(Locale.forLanguageTag("en-US"));
    BigDecimal fromEnglishHost = TurkishNumbers.parseAmount("1.234,56");

    Locale.setDefault(Locale.forLanguageTag("tr-TR"));
    BigDecimal fromTurkishHost = TurkishNumbers.parseAmount("1.234,56");

    assertThat(fromEnglishHost)
        .isEqualByComparingTo(fromTurkishHost)
        .isEqualByComparingTo("1234.56");
  }
}
