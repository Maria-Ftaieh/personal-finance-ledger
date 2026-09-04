package dev.ledger.app.web;

import dev.ledger.app.domain.RuleEntity;
import dev.ledger.app.service.CategorisationService;
import dev.ledger.app.service.RuleService;
import dev.ledger.app.service.RuleService.RuleDraft;
import dev.ledger.core.rules.MatchType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/rules")
public class RuleController {

  private final RuleService rules;
  private final CategorisationService categorisation;

  public RuleController(RuleService rules, CategorisationService categorisation) {
    this.rules = rules;
    this.categorisation = categorisation;
  }

  /** In evaluation order: priority ascending, then id. First match wins (SPEC §5.3). */
  @GetMapping
  public List<RuleView> list() {
    return rules.list().stream().map(RuleView::of).toList();
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public RuleView create(@Valid @RequestBody RuleRequest body) {
    return RuleView.of(rules.create(body.toDraft()));
  }

  @PutMapping("/{id}")
  public RuleView update(@PathVariable UUID id, @Valid @RequestBody RuleRequest body) {
    return RuleView.of(rules.update(id, body.toDraft()));
  }

  @DeleteMapping("/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void delete(@PathVariable UUID id) {
    rules.delete(id);
  }

  /**
   * How many transactions the current rule set would move, without moving any.
   *
   * <p>SPEC §5.3 requires this before a bulk recategorisation: edit a rule and a year of history
   * can change shape, so the count comes first and the commit is a separate call.
   */
  @GetMapping("/preview")
  public CategorisationService.Preview preview() {
    return categorisation.preview();
  }

  /** Commits the recategorisation. Manually assigned transactions are left alone. */
  @PostMapping("/reevaluate")
  public ReevaluationResult reevaluate() {
    return new ReevaluationResult(categorisation.reevaluateAll());
  }

  public record ReevaluationResult(int changed) {}

  public record RuleRequest(
      int priority,
      @NotNull MatchType matchType,
      @NotBlank String pattern,
      @NotBlank String categoryId,
      String subcategoryId) {

    RuleDraft toDraft() {
      return new RuleDraft(priority, matchType, pattern, categoryId, subcategoryId);
    }
  }

  public record RuleView(
      UUID id,
      int priority,
      MatchType matchType,
      String pattern,
      String categoryId,
      String subcategoryId,
      boolean userDefined) {

    static RuleView of(RuleEntity entity) {
      return new RuleView(
          entity.getId(),
          entity.getPriority(),
          entity.getMatchType(),
          entity.getPattern(),
          entity.getCategoryId(),
          entity.getSubcategoryId(),
          entity.isUserDefined());
    }
  }
}
