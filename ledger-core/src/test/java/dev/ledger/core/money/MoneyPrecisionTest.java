package dev.ledger.core.money;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * The one-screen argument for why {@link Money} exists.
 *
 * <p>SPEC §3.1. Keep this test. Binary floating point has no exact representation of 0.1 or 0.2, so
 * a currency amount held in a {@code double} is wrong from the moment it is written down, and the
 * error compounds with every addition. The failures below are not exotic — they are the two most
 * ordinary prices in a shop.
 */
class MoneyPrecisionTest {

  @ParameterizedTest(name = "{0} + {1} = {2}")
  @CsvSource({
    "0.10, 0.20, 0.30",
    "0.01, 0.02, 0.03",
    "1.10, 2.20, 3.30",
    "9.99, 0.01, 10.00",
    "0.07, 0.01, 0.08",
    "1234.56, 0.44, 1235.00",
    "-50.00, 50.00, 0.00"
  })
  @DisplayName("Money addition is exact")
  void moneyAdditionIsExact(String left, String right, String expected) {
    Money sum = Money.tryLira(left).plus(Money.tryLira(right));

    assertThat(sum).isEqualTo(Money.tryLira(expected));
    assertThat(sum.toString()).isEqualTo(new BigDecimal(expected).setScale(2).toPlainString());
  }

  @ParameterizedTest(name = "double: {0} + {1} != {2}")
  @CsvSource({"0.1, 0.2, 0.3", "1.1, 2.2, 3.3", "0.1, 0.7, 0.8"})
  @DisplayName("the same sums in double are wrong")
  void doubleAdditionIsNotExact(double left, double right, double expected) {
    double sum = left + right;

    assertThat(sum).isNotEqualTo(expected);
    // 0.1 + 0.2 is 0.30000000000000004, not 0.3.
    assertThat(sum).isCloseTo(expected, org.assertj.core.data.Offset.offset(1e-9));
  }

  @Test
  @DisplayName("a hundred ten-kuruş items cost exactly ten lira, and in double they do not")
  void accumulationDivergesInDouble() {
    Money moneyTotal = Money.zero(Money.TRY);
    double doubleTotal = 0.0;
    for (int i = 0; i < 100; i++) {
      moneyTotal = moneyTotal.plus(Money.tryLira("0.10"));
      doubleTotal += 0.10;
    }

    assertThat(moneyTotal).isEqualTo(Money.tryLira("10.00"));
    assertThat(doubleTotal).isNotEqualTo(10.0);
  }
}
