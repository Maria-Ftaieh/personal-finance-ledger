package dev.ledger.core.rules;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class RegexGuardTest {

  @ParameterizedTest
  @ValueSource(strings = {"(a+)+b", "(a*)*c", "([a-z]+)*$", "(x+x+)+y"})
  @DisplayName("patterns that can backtrack catastrophically are refused at save time")
  void rejectsNestedQuantifiers(String pattern) {
    assertThatThrownBy(() -> RegexGuard.compile(pattern))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("backtrack");
  }

  @Test
  @DisplayName("a malformed pattern is refused, not stored")
  void rejectsMalformedPatterns() {
    assertThatThrownBy(() -> RegexGuard.compile("(unclosed"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> RegexGuard.compile(" ")).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> RegexGuard.compile("a".repeat(RegexGuard.MAX_PATTERN_LENGTH + 1)))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  @DisplayName("a rule carrying a dangerous regex cannot be constructed at all")
  void ruleConstructionValidatesTheRegex() {
    assertThatThrownBy(
            () ->
                new CategorisationRule(
                    RuleId.newId(),
                    1,
                    MatchType.REGEX,
                    "(a+)+b",
                    CategoryId.of("Yemek"),
                    SubcategoryId.of("Kahve"),
                    true))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  @DisplayName("ordinary patterns compile and match, case insensitively")
  void allowsOrdinaryPatterns() {
    Pattern pattern = RegexGuard.compile("^(a101|bim|sok)\\b");

    assertThat(RegexGuard.findWithin(pattern, "A101 KADIKOY")).isTrue();
    assertThat(RegexGuard.findWithin(pattern, "MIGROS")).isFalse();
  }

  @Test
  @DisplayName("the step budget stops a slow match that slipped past the static check")
  void stepBudgetBoundsMatchTime() {
    // Compiled directly, bypassing the save-time check, to prove the second defence works on its
    // own: this is the shape a cleverer pattern would use to evade the static rule.
    Pattern evil = Pattern.compile("(?:a|aa)+$");
    String input = "a".repeat(60) + "b";

    long startedAt = System.nanoTime();
    boolean matched = RegexGuard.findWithin(evil, input);
    Duration elapsed = Duration.ofNanos(System.nanoTime() - startedAt);

    assertThat(matched).isFalse();
    assertThat(elapsed).isLessThan(Duration.ofSeconds(2));
  }
}
