package dev.ledger.core.parse;

import dev.ledger.core.text.TurkishText;
import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.text.ParsePosition;
import java.util.regex.Pattern;

/**
 * Parses amounts as they are printed on Turkish statements: {@code 1.234,56} — dot groups
 * thousands, comma marks the decimal.
 *
 * <p>SPEC §3.2. Neither {@code Double.parseDouble} nor {@code new BigDecimal(String)} may touch
 * this text. {@code new BigDecimal("1.234,56")} throws; worse, {@code new BigDecimal("1.234")}
 * succeeds and quietly returns one and a bit instead of one thousand two hundred thirty four. That
 * is a factor of a thousand, silently, on a number the user trusts.
 *
 * <p>The parse is deliberately strict: the whole string must be consumed, so a half-recognised
 * amount fails loudly rather than truncating.
 */
public final class TurkishNumbers {

  /**
   * Locale-fixed symbols, not {@code DecimalFormat.getInstance(locale)}, so the JVM default locale
   * and any CLDR change cannot alter how a statement is read.
   */
  private static final DecimalFormatSymbols TR_SYMBOLS = trSymbols();

  /** Currency markers a bank may print next to the figure. */
  private static final Pattern CURRENCY_NOISE =
      Pattern.compile("(?i)\\s*(₺|TL|TRY|EUR|USD|\\$|€)\\s*");

  private static final Pattern WHITESPACE = Pattern.compile("[\\s\\u00A0\\u202F]+");

  private TurkishNumbers() {}

  private static DecimalFormatSymbols trSymbols() {
    DecimalFormatSymbols symbols = new DecimalFormatSymbols(TurkishText.TURKISH);
    symbols.setDecimalSeparator(',');
    symbols.setGroupingSeparator('.');
    return symbols;
  }

  /**
   * Reads a signed amount. Accepts a leading {@code -}/{@code +} or the trailing {@code -} that
   * several Turkish banks use to mark a refund ({@code "1.234,56-"}).
   *
   * @throws IllegalArgumentException if the text is not a complete Turkish-formatted number
   */
  public static BigDecimal parseAmount(String raw) {
    if (raw == null) {
      throw new IllegalArgumentException("amount text is null");
    }
    String text = WHITESPACE.matcher(TurkishText.normalise(raw)).replaceAll("");
    text = CURRENCY_NOISE.matcher(text).replaceAll("");

    boolean negative = false;
    if (text.endsWith("-")) {
      // Trailing minus: how Garanti and several others print a refund.
      negative = true;
      text = text.substring(0, text.length() - 1);
    } else if (text.startsWith("-")) {
      negative = true;
      text = text.substring(1);
    } else if (text.startsWith("+")) {
      text = text.substring(1);
    }

    if (text.isEmpty()) {
      throw new IllegalArgumentException("not a Turkish amount: '" + raw + "'");
    }

    DecimalFormat format = new DecimalFormat("#,##0.###", TR_SYMBOLS);
    format.setParseBigDecimal(true);
    ParsePosition position = new ParsePosition(0);
    Number parsed = format.parse(text, position);
    if (parsed == null || position.getIndex() != text.length()) {
      throw new IllegalArgumentException("not a Turkish amount: '" + raw + "'");
    }

    BigDecimal value = (BigDecimal) parsed;
    return negative ? value.negate() : value;
  }

  /** True when the text parses as an amount, for cheap column sniffing during PDF parsing. */
  public static boolean isAmount(String raw) {
    try {
      parseAmount(raw);
      return true;
    } catch (IllegalArgumentException e) {
      return false;
    }
  }
}
