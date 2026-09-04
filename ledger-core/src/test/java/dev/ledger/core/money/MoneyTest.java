package dev.ledger.core.money;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class MoneyTest {

  @Test
  @DisplayName("mixing currencies throws rather than coercing")
  void mixingCurrenciesThrows() {
    Money lira = Money.tryLira("100.00");
    Money euro = Money.of("100.00", Money.EUR);

    assertThatThrownBy(() -> lira.plus(euro))
        .isInstanceOf(CurrencyMismatchException.class)
        .hasMessageContaining("TRY")
        .hasMessageContaining("EUR");
    assertThatThrownBy(() -> lira.minus(euro)).isInstanceOf(CurrencyMismatchException.class);
    assertThatThrownBy(() -> lira.compareTo(euro)).isInstanceOf(CurrencyMismatchException.class);
  }

  @Test
  @DisplayName("scale is normalised, so 1.5 and 1.50 are the same value")
  void scaleIsNormalised() {
    assertThat(Money.tryLira("1.5")).isEqualTo(Money.tryLira("1.50"));
    assertThat(Money.tryLira("1.5").amount().scale()).isEqualTo(2);
  }

  @ParameterizedTest(name = "{0} rounds to {1}")
  @CsvSource({"1.005, 1.01", "1.004, 1.00", "2.675, 2.68", "-1.005, -1.01", "0.125, 0.13"})
  @DisplayName("HALF_UP, the way the statement rounds")
  void roundsHalfUp(String raw, String expected) {
    assertThat(Money.of(new BigDecimal(raw), Money.TRY)).isEqualTo(Money.tryLira(expected));
  }

  @Test
  @DisplayName("a refund is a negative amount, not a flag")
  void refundsAreNegative() {
    Money refund = Money.tryLira("-249.90");

    assertThat(refund.isNegative()).isTrue();
    assertThat(refund.abs()).isEqualTo(Money.tryLira("249.90"));
    assertThat(Money.tryLira("249.90").plus(refund)).isEqualTo(Money.zero(Money.TRY));
  }

  @Test
  @DisplayName("toString is the JSON form: a string, never a number")
  void toStringIsPlain() {
    assertThat(Money.tryLira("1234.56")).hasToString("1234.56");
    assertThat(Money.tryLira("-0.05")).hasToString("-0.05");
  }

  @Test
  @DisplayName("display formatting is Turkish; matching never uses it")
  void formatsForDisplay() {
    String formatted = Money.tryLira("1234.56").format(dev.ledger.core.text.TurkishText.TURKISH);

    assertThat(formatted).contains("1.234,56");
  }

  @Test
  @DisplayName("division keeps intermediate precision, as inflation deflation needs")
  void dividesWithRounding() {
    Money deflated = Money.tryLira("6200.00").multiply(new BigDecimal("0.7431"));

    assertThat(deflated).isEqualTo(Money.tryLira("4607.22"));
  }
}
