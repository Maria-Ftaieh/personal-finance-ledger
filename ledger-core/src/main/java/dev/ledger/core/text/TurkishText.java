package dev.ledger.core.text;

import java.text.Normalizer;
import java.util.Locale;

/**
 * Case folding and Unicode normalisation, with the Turkish traps handled explicitly.
 *
 * <p>SPEC §3.2. The dotted/dotless I is the bug that motivates this class. Turkish has four i
 * letters — {@code i İ ı I} — and its case mapping pairs them {@code i↔İ} and {@code ı↔I}, unlike
 * every other locale. So on a JVM whose default locale is {@code tr-TR}:
 *
 * <pre>{@code
 * "TITLE".toLowerCase()  // -> "tıtle"  on tr-TR, "title" everywhere else
 * "istanbul".toUpperCase() // -> "İSTANBUL" on tr-TR, "ISTANBUL" everywhere else
 * }</pre>
 *
 * <p>Any comparison written with the no-argument {@code toLowerCase()} is therefore a latent bug
 * that only appears on a Turkish machine — which, for this application, is the likely machine. The
 * rule is absolute:
 *
 * <ul>
 *   <li><b>Matching</b> (rules, dedup, fingerprints) folds with {@link Locale#ROOT}. Matching is
 *       machine-internal and must give the same answer on every host.
 *   <li><b>Display</b> (anything a human reads) folds with {@link #TURKISH}, because "istanbul"
 *       shown to a Turkish user should title-case to "İstanbul", not "Istanbul".
 * </ul>
 */
public final class TurkishText {

  public static final Locale TURKISH = Locale.forLanguageTag("tr-TR");

  private TurkishText() {}

  /**
   * Canonical composition. Applied at the extraction boundary: PDF text layers hand back {@code ş}
   * as either U+015F or as {@code s} plus a combining cedilla, and those two are different strings
   * to {@code equals} while being the same letter to a reader.
   */
  public static String normalise(String text) {
    if (text == null) {
      return null;
    }
    return Normalizer.isNormalized(text, Normalizer.Form.NFC)
        ? text
        : Normalizer.normalize(text, Normalizer.Form.NFC);
  }

  /** Lower cases for matching logic. Locale independent by construction. */
  public static String lowerForMatching(String text) {
    return text == null ? null : text.toLowerCase(Locale.ROOT);
  }

  /** Upper cases for matching logic. Locale independent by construction. */
  public static String upperForMatching(String text) {
    return text == null ? null : text.toUpperCase(Locale.ROOT);
  }

  /** Lower cases for human display, honouring Turkish {@code I → ı} and {@code İ → i}. */
  public static String lowerForDisplay(String text) {
    return text == null ? null : text.toLowerCase(TURKISH);
  }

  /** Upper cases for human display, honouring Turkish {@code i → İ} and {@code ı → I}. */
  public static String upperForDisplay(String text) {
    return text == null ? null : text.toUpperCase(TURKISH);
  }

  /**
   * Folds Turkish letters to their nearest ASCII equivalents, for matching only — never for
   * storage. A merchant string reaches us as "KAHVE DÜNYASI" from one bank and "KAHVE DUNYASI" from
   * another; folding makes them comparable without destroying the original, which {@code
   * Transaction.rawDescription} keeps verbatim.
   *
   * <p>{@code ı} (U+0131) has no canonical decomposition, so it cannot be handled by stripping
   * combining marks and is mapped by hand along with its siblings.
   */
  public static String foldToAscii(String text) {
    if (text == null) {
      return null;
    }
    StringBuilder folded = new StringBuilder(text.length());
    for (int i = 0; i < text.length(); i++) {
      char c = text.charAt(i);
      switch (c) {
        case 'ı', 'İ' -> folded.append(c == 'ı' ? 'i' : 'I');
        case 'ğ' -> folded.append('g');
        case 'Ğ' -> folded.append('G');
        case 'ş' -> folded.append('s');
        case 'Ş' -> folded.append('S');
        case 'ç' -> folded.append('c');
        case 'Ç' -> folded.append('C');
        case 'ö' -> folded.append('o');
        case 'Ö' -> folded.append('O');
        case 'ü' -> folded.append('u');
        case 'Ü' -> folded.append('U');
        case 'â', 'î', 'û' -> folded.append(c == 'â' ? 'a' : c == 'î' ? 'i' : 'u');
        default -> folded.append(c);
      }
    }
    // Anything still carrying an accent (é, ñ, and the decomposed forms of the letters above)
    // is decomposed and its combining marks dropped.
    String decomposed = Normalizer.normalize(folded.toString(), Normalizer.Form.NFD);
    return decomposed.replaceAll("\\p{M}+", "");
  }
}
