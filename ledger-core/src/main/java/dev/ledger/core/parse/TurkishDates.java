package dev.ledger.core.parse;

import dev.ledger.core.text.TurkishText;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses the date formats Turkish card statements actually use.
 *
 * <p>SPEC §3.2. Numeric forms are {@code dd.MM.yyyy} and {@code dd/MM/yyyy}; some banks print the
 * month as a Turkish name or three-letter abbreviation ({@code 20 Oca 2026}).
 *
 * <p>The month names come from the table below rather than from {@code DateTimeFormatter} with a
 * Turkish locale. The JDK's CLDR data is a moving target across releases, and banks do not all
 * abbreviate the way CLDR does — some print {@code Ağu}, some {@code AGU}, some the full {@code
 * Ağustos}. A table we own accepts every spelling we have seen, is folded to ASCII first so a
 * mangled PDF text layer still matches, and cannot change under us on a JDK upgrade. Once the month
 * token is resolved to a number, {@link DateTimeFormatter} does the actual date construction, so
 * impossible dates such as 31.02 are still rejected.
 */
public final class TurkishDates {

  /** ASCII-folded, upper cased month tokens → month number. Longest key wins. */
  private static final Map<String, Integer> MONTHS =
      Map.ofEntries(
          Map.entry("OCAK", 1), Map.entry("OCA", 1),
          Map.entry("SUBAT", 2), Map.entry("SUB", 2),
          Map.entry("MART", 3), Map.entry("MAR", 3),
          Map.entry("NISAN", 4), Map.entry("NIS", 4),
          Map.entry("MAYIS", 5), Map.entry("MAY", 5),
          Map.entry("HAZIRAN", 6), Map.entry("HAZ", 6),
          Map.entry("TEMMUZ", 7), Map.entry("TEM", 7),
          Map.entry("AGUSTOS", 8), Map.entry("AGU", 8),
          Map.entry("EYLUL", 9), Map.entry("EYL", 9),
          Map.entry("EKIM", 10), Map.entry("EKI", 10),
          Map.entry("KASIM", 11), Map.entry("KAS", 11),
          Map.entry("ARALIK", 12), Map.entry("ARA", 12));

  private static final DateTimeFormatter STRICT_DMY =
      DateTimeFormatter.ofPattern("dd.MM.uuuu", Locale.ROOT)
          .withResolverStyle(ResolverStyle.STRICT);

  private static final Pattern NUMERIC =
      Pattern.compile("^(\\d{1,2})[./\\-](\\d{1,2})[./\\-](\\d{2}|\\d{4})$");

  private static final Pattern TEXTUAL =
      Pattern.compile("^(\\d{1,2})\\s*[.\\-]?\\s*([A-Z]{3,8})\\s*[.\\-]?\\s*(\\d{2}|\\d{4})$");

  /** A two-digit year on a card statement is this century; these documents are not historical. */
  private static final int CENTURY = 2000;

  private TurkishDates() {}

  /**
   * @throws DateTimeParseException if the text is not a date this application recognises
   */
  public static LocalDate parse(String raw) {
    if (raw == null) {
      throw new DateTimeParseException("date text is null", "", 0);
    }
    String text = TurkishText.normalise(raw).trim().replaceAll("[\\s\\u00A0]+", " ");

    Matcher numeric = NUMERIC.matcher(text);
    if (numeric.matches()) {
      return build(numeric.group(1), Integer.parseInt(numeric.group(2)), numeric.group(3), raw);
    }

    String folded = TurkishText.upperForMatching(TurkishText.foldToAscii(text));
    Matcher textual = TEXTUAL.matcher(folded);
    if (textual.matches()) {
      Integer month = MONTHS.get(textual.group(2));
      if (month != null) {
        return build(textual.group(1), month, textual.group(3), raw);
      }
    }

    throw new DateTimeParseException("unrecognised date format", raw, 0);
  }

  /** True when the text is a date, for cheap column sniffing during PDF parsing. */
  public static boolean isDate(String raw) {
    try {
      parse(raw);
      return true;
    } catch (DateTimeParseException e) {
      return false;
    }
  }

  private static LocalDate build(String day, int month, String year, String raw) {
    int resolvedYear =
        year.length() == 2 ? CENTURY + Integer.parseInt(year) : Integer.parseInt(year);
    String canonical =
        String.format(Locale.ROOT, "%02d.%02d.%04d", Integer.parseInt(day), month, resolvedYear);
    try {
      return LocalDate.parse(canonical, STRICT_DMY);
    } catch (DateTimeParseException e) {
      throw new DateTimeParseException("not a valid calendar date: " + raw, raw, 0);
    }
  }
}
