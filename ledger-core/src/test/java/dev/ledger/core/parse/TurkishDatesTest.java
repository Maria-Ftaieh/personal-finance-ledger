package dev.ledger.core.parse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Locale;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class TurkishDatesTest {

  private final Locale original = Locale.getDefault();

  @AfterEach
  void restoreLocale() {
    Locale.setDefault(original);
  }

  @ParameterizedTest(name = "[{0}] -> {1}")
  @CsvSource({
    "'20.01.2026', 2026-01-20",
    "'20/01/2026', 2026-01-20",
    "'01-02-2026', 2026-02-01",
    "'5.3.2026', 2026-03-05",
    "'20.01.26', 2026-01-20",
    "'20 Oca 2026', 2026-01-20",
    "'03 Şub 2026', 2026-02-03",
    "'15 Ağu 2025', 2025-08-15",
    "'01 Eki 2025', 2025-10-01",
    "'31 Ara 2025', 2025-12-31",
    "'09 ARALIK 2025', 2025-12-09",
    "'09 Aralık 2025', 2025-12-09",
    "'17 MAYIS 2026', 2026-05-17",
    "'02 Agu 2025', 2025-08-02",
    "'02 SUB 2026', 2026-02-02"
  })
  @DisplayName("reads every date shape the supported banks print")
  void parsesTurkishDates(String raw, LocalDate expected) {
    assertThat(TurkishDates.parse(raw)).isEqualTo(expected);
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "31.02.2026",
        "00.01.2026",
        "20.13.2026",
        "20 Xyz 2026",
        "2026-01-20",
        "",
        "20.01"
      })
  @DisplayName("an impossible or unknown date is rejected, not guessed at")
  void rejectsBadDates(String raw) {
    assertThatThrownBy(() -> TurkishDates.parse(raw)).isInstanceOf(DateTimeParseException.class);
  }

  @Test
  @DisplayName("month names resolve from our own table, not from the JVM's CLDR data")
  void independentOfDefaultLocale() {
    Locale.setDefault(Locale.forLanguageTag("en-US"));
    assertThat(TurkishDates.parse("15 Ağu 2025")).isEqualTo(LocalDate.of(2025, 8, 15));

    Locale.setDefault(Locale.forLanguageTag("tr-TR"));
    assertThat(TurkishDates.parse("15 Ağu 2025")).isEqualTo(LocalDate.of(2025, 8, 15));
  }

  @Test
  @DisplayName("isDate is a cheap column sniff and never throws")
  void sniffsColumns() {
    assertThat(TurkishDates.isDate("20.01.2026")).isTrue();
    assertThat(TurkishDates.isDate("MIGROS TICARET")).isFalse();
  }
}
