package dev.ledger.app.web;

import dev.ledger.app.service.DuplicateService;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The duplicate review queue. SPEC §3.4: the pass flags, the user decides, and nothing is deleted
 * either way.
 */
@RestController
@RequestMapping("/api/duplicates")
public class DuplicateController {

  private final DuplicateService duplicates;

  public DuplicateController(DuplicateService duplicates) {
    this.duplicates = duplicates;
  }

  @GetMapping
  public List<TransactionView> queue() {
    return duplicates.reviewQueue().stream().map(TransactionView::of).toList();
  }

  /** Yes, this repeats an earlier line. It stops counting towards totals; the row stays. */
  @PostMapping("/{id}/confirm")
  public TransactionView confirm(@PathVariable UUID id) {
    return TransactionView.of(duplicates.confirm(id));
  }

  /** No, it is a separate purchase. It counts, and a later pass will not flag it again. */
  @PostMapping("/{id}/reject")
  public TransactionView reject(@PathVariable UUID id) {
    return TransactionView.of(duplicates.reject(id));
  }
}
