package dev.ledger.core.text;

import java.util.regex.Pattern;

/**
 * Derives the matching form of a merchant description from the raw statement text.
 *
 * <p>SPEC §3.3/§3.4. {@code rawDescription} is kept verbatim forever; this is the derived form that
 * deduplication and the categorisation rules compare against, and it can be recomputed for every
 * stored transaction whenever this class improves — which is the whole reason the raw text is never
 * overwritten.
 *
 * <p>The steps, in order:
 *
 * <ol>
 *   <li>NFC normalise, so composed and decomposed {@code ş} compare equal.
 *   <li>Fold to ASCII, so "KAHVE DÜNYASI" and a mis-extracted "KAHVE DUNYASI" compare equal.
 *   <li>Upper case with {@link java.util.Locale#ROOT} — never the default locale (§3.2).
 *   <li>Drop instalment markers ({@code 3/8}); the same purchase carries a different marker in
 *       consecutive statements, so leaving them in would defeat dedup.
 *   <li>Drop trailing digit runs of six or more. Banks append a per-statement authorisation
 *       reference which differs between two printings of the same purchase.
 *   <li>Collapse runs of whitespace and punctuation noise to single spaces.
 * </ol>
 */
public final class DescriptionNormalizer {

  /** A run of six or more digits at the end of the string: an authorisation or reference number. */
  private static final Pattern TRAILING_REFERENCE = Pattern.compile("[\\s*#/-]*\\d{6,}\\s*$");

  /** {@code 3/8}, but not the {@code 01/2026} of an embedded date. */
  private static final Pattern INSTALLMENT_MARKER =
      Pattern.compile("(?<![\\d/])(\\d{1,2})\\s*/\\s*(\\d{1,2})(?![\\d/])");

  private static final Pattern PUNCTUATION_NOISE = Pattern.compile("[^A-Z0-9]+");

  private DescriptionNormalizer() {}

  public static String normalise(String rawDescription) {
    if (rawDescription == null) {
      return "";
    }
    String text =
        TurkishText.upperForMatching(
            TurkishText.foldToAscii(TurkishText.normalise(rawDescription)));
    text = INSTALLMENT_MARKER.matcher(text).replaceAll(" ");

    // Reference numbers can be stacked ("... 1234567 000998877"); strip until stable.
    String previous;
    do {
      previous = text;
      text = TRAILING_REFERENCE.matcher(text).replaceAll("");
    } while (!text.equals(previous));

    return PUNCTUATION_NOISE.matcher(text).replaceAll(" ").trim();
  }
}
