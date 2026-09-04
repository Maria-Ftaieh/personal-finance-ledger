package dev.ledger.app.web;

import dev.ledger.app.config.CurrentUser;
import dev.ledger.app.repo.TransactionRepository;
import dev.ledger.app.service.CategorisationService;
import jakarta.validation.constraints.NotBlank;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/transactions")
public class TransactionController {

  private final TransactionRepository transactions;
  private final CategorisationService categorisation;

  public TransactionController(
      TransactionRepository transactions, CategorisationService categorisation) {
    this.transactions = transactions;
    this.categorisation = categorisation;
  }

  /**
   * @param includeConfirmedDuplicates off by default: a confirmed duplicate is still on the record
   *     but is not part of what the user spent, so it must not appear in an ordinary listing
   */
  @GetMapping
  public List<TransactionView> search(
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
      @RequestParam(required = false) String categoryId,
      @RequestParam(defaultValue = "false") boolean includeConfirmedDuplicates) {

    return transactions
        .search(CurrentUser.ID, from, to, categoryId, includeConfirmedDuplicates)
        .stream()
        .map(TransactionView::of)
        .toList();
  }

  /** Assigns a category by hand. Survives every later rule re-evaluation (SPEC §5.3). */
  @PutMapping("/{id}/category")
  public TransactionView assign(@PathVariable UUID id, @RequestBody CategoryAssignment body) {
    return TransactionView.of(
        categorisation.assignManually(id, body.categoryId(), body.subcategoryId()));
  }

  /** Drops the manual assignment and lets the rules decide again. */
  @DeleteMapping("/{id}/category")
  public TransactionView clearAssignment(@PathVariable UUID id) {
    return TransactionView.of(categorisation.clearManualAssignment(id));
  }

  public record CategoryAssignment(@NotBlank String categoryId, String subcategoryId) {}
}
