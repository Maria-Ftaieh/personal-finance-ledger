package dev.ledger.core.rules;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ledger.core.text.DescriptionNormalizer;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class RuleEngineTest {

  private final Locale original = Locale.getDefault();

  @AfterEach
  void restoreLocale() {
    Locale.setDefault(original);
  }

  private static CategorisationRule rule(
      int priority, MatchType type, String pattern, String category, boolean userDefined) {
    return new CategorisationRule(
        RuleId.newId(),
        priority,
        type,
        pattern,
        CategoryId.of(category),
        SubcategoryId.of(category + "/sub"),
        userDefined);
  }

  private static Optional<String> categoryFor(RuleEngine engine, String rawDescription) {
    return engine
        .match(DescriptionNormalizer.normalise(rawDescription))
        .map(r -> r.category().value());
  }

  @Test
  @DisplayName("first match wins; a lower priority number decides")
  void firstMatchWins() {
    RuleEngine engine =
        new RuleEngine(
            List.of(
                rule(20, MatchType.CONTAINS, "KAHVE", "Yemek", false),
                rule(10, MatchType.CONTAINS, "KAHVE DUNYASI", "Kahve", false)));

    assertThat(categoryFor(engine, "KAHVE DÜNYASI KADIKÖY")).contains("Kahve");
  }

  @Test
  @DisplayName("a user rule can outrank a system rule; the priority space is not partitioned")
  void userRulesCanOutrankSystemRules() {
    CategorisationRule system = rule(50, MatchType.CONTAINS, "MIGROS", "Yemek", false);
    CategorisationRule user = rule(1, MatchType.CONTAINS, "MIGROS", "Konut", true);

    assertThat(categoryFor(new RuleEngine(List.of(system, user)), "MIGROS TICARET AS"))
        .contains("Konut");
    // Order of construction must not matter, only priority.
    assertThat(categoryFor(new RuleEngine(List.of(user, system)), "MIGROS TICARET AS"))
        .contains("Konut");
  }

  @Test
  @DisplayName("every match type behaves as named")
  void matchTypes() {
    RuleEngine contains =
        new RuleEngine(List.of(rule(1, MatchType.CONTAINS, "SPOTIFY", "Dijital", false)));
    RuleEngine startsWith =
        new RuleEngine(List.of(rule(1, MatchType.STARTS_WITH, "SHELL", "Ulasim", false)));
    RuleEngine exact = new RuleEngine(List.of(rule(1, MatchType.EXACT, "GETIR", "Yemek", false)));
    RuleEngine regex =
        new RuleEngine(List.of(rule(1, MatchType.REGEX, "^(A101|BIM)\\b", "Market", false)));

    assertThat(categoryFor(contains, "PAYPAL *SPOTIFY AB")).contains("Dijital");
    assertThat(categoryFor(startsWith, "SHELL AKARYAKIT")).contains("Ulasim");
    assertThat(categoryFor(startsWith, "OPET SHELL")).isEmpty();
    assertThat(categoryFor(exact, "GETIR")).contains("Yemek");
    assertThat(categoryFor(exact, "GETIR YEMEK")).isEmpty();
    assertThat(categoryFor(regex, "A101 KADIKOY")).contains("Market");
    assertThat(categoryFor(regex, "BIM BIRLESIK")).contains("Market");
    assertThat(categoryFor(regex, "MIGROS")).isEmpty();
  }

  @Test
  @DisplayName("an unmatched description falls through rather than guessing")
  void noMatchIsEmpty() {
    RuleEngine engine =
        new RuleEngine(List.of(rule(1, MatchType.CONTAINS, "SPOTIFY", "Dijital", false)));

    assertThat(categoryFor(engine, "KUAFOR AYSE")).isEmpty();
  }

  @Test
  @DisplayName("SPEC §3.2: the engine still matches on a machine whose default locale is Turkish")
  void matchesUnderTurkishDefaultLocale() {
    // The bug this guards: with a tr-TR default locale, a rule engine written with the
    // no-argument toLowerCase() folds "TITLE" to "tıtle" and quietly matches nothing.
    Locale.setDefault(Locale.forLanguageTag("tr-TR"));

    RuleEngine engine =
        new RuleEngine(
            List.of(
                rule(10, MatchType.CONTAINS, "İSTANBUL", "Seyahat", false),
                rule(20, MatchType.CONTAINS, "kahve dünyası", "Kahve", true),
                rule(30, MatchType.STARTS_WITH, "TİTİZ", "Market", false)));

    assertThat(categoryFor(engine, "THY İSTANBUL")).contains("Seyahat");
    assertThat(categoryFor(engine, "KAHVE DUNYASI ISTANBUL")).contains("Seyahat");
    assertThat(categoryFor(engine, "Kahve Dünyası Kadıköy")).contains("Kahve");
    assertThat(categoryFor(engine, "TITIZ MARKET")).contains("Market");
  }

  @Test
  @DisplayName("rules are exposed in evaluation order")
  void exposesEvaluationOrder() {
    CategorisationRule low = rule(5, MatchType.CONTAINS, "A", "One", false);
    CategorisationRule high = rule(50, MatchType.CONTAINS, "B", "Two", false);

    assertThat(new RuleEngine(List.of(high, low)).rules()).containsExactly(low, high);
  }
}
