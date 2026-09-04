package dev.ledger.app.service;

import dev.ledger.app.config.CurrentUser;
import dev.ledger.app.domain.DuplicateStatus;
import dev.ledger.app.domain.StatementEntity;
import dev.ledger.app.domain.TransactionEntity;
import dev.ledger.app.repo.StatementRepository;
import dev.ledger.app.repo.TransactionRepository;
import dev.ledger.core.dedup.DedupReport;
import dev.ledger.core.dedup.DuplicateDetector;
import dev.ledger.core.dedup.DuplicateMatch;
import dev.ledger.core.model.DateRange;
import dev.ledger.core.model.ParsedStatement;
import dev.ledger.core.model.StatementId;
import dev.ledger.core.model.Transaction;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Runs the deduplication pass over stored statements and keeps the review queue.
 *
 * <p>SPEC §3.4: nothing is ever deleted. A match sets a flag and a pointer to the transaction it
 * appears to repeat, and the user confirms or rejects it.
 */
@Service
public class DuplicateService {

  private final StatementRepository statements;
  private final TransactionRepository transactions;
  private final DuplicateDetector detector = new DuplicateDetector();

  public DuplicateService(StatementRepository statements, TransactionRepository transactions) {
    this.statements = statements;
    this.transactions = transactions;
  }

  /**
   * Compares a freshly imported statement against every stored statement whose period overlaps it,
   * and flags what the detector matches.
   *
   * <p>The newly imported statement is not necessarily the later one — a user may upload January
   * after February — so the detector decides which side is the suspect from the periods, and this
   * method persists whichever side it names. A decision the user has already made is never
   * overwritten.
   *
   * @return how many rows were newly flagged
   */
  @Transactional
  public int flagAgainstOverlapping(
      StatementEntity imported, List<TransactionEntity> importedRows) {
    List<StatementEntity> overlapping =
        statements.findOverlapping(
            CurrentUser.ID, imported.getId(), imported.getPeriodStart(), imported.getPeriodEnd());
    if (overlapping.isEmpty()) {
      return 0;
    }

    Map<UUID, TransactionEntity> byId = new HashMap<>();
    importedRows.forEach(row -> byId.put(row.getId(), row));

    List<ParsedStatement> views = new ArrayList<>();
    views.add(view(imported, importedRows));
    for (StatementEntity existing : overlapping) {
      List<TransactionEntity> rows =
          transactions.findByStatementIdOrderByTransactionDateAscIdAsc(existing.getId());
      rows.forEach(row -> byId.put(row.getId(), row));
      views.add(view(existing, rows));
    }

    DedupReport report = detector.detect(views);
    int flagged = 0;
    for (DuplicateMatch match : report.matches()) {
      TransactionEntity suspect = byId.get(match.duplicate().value());
      if (suspect == null || suspect.getDuplicateStatus() != DuplicateStatus.NONE) {
        // Already flagged, or the user has already confirmed or rejected it. Their decision
        // stands; re-running the pass must not quietly reopen a question they answered.
        continue;
      }
      suspect.flagAsSuspectedDuplicate(
          match.original().value(),
          BigDecimal.valueOf(match.similarity()).setScale(4, java.math.RoundingMode.HALF_UP),
          match.reason().name());
      flagged++;
    }
    transactions.saveAll(byId.values());
    return flagged;
  }

  @Transactional(readOnly = true)
  public List<TransactionEntity> reviewQueue() {
    return transactions.findByUserIdAndDuplicateStatusOrderByTransactionDateDesc(
        CurrentUser.ID, DuplicateStatus.SUSPECTED);
  }

  /** The user agrees it is a duplicate: excluded from totals, still on the record. */
  @Transactional
  public TransactionEntity confirm(UUID transactionId) {
    TransactionEntity suspect = suspected(transactionId);
    suspect.confirmDuplicate();
    return transactions.save(suspect);
  }

  /** The user says it is a genuine separate purchase: counted, and never flagged again. */
  @Transactional
  public TransactionEntity reject(UUID transactionId) {
    TransactionEntity suspect = suspected(transactionId);
    suspect.rejectDuplicate();
    return transactions.save(suspect);
  }

  private TransactionEntity suspected(UUID transactionId) {
    TransactionEntity suspect =
        transactions
            .findById(transactionId)
            .filter(row -> row.getUserId().equals(CurrentUser.ID))
            .orElseThrow(() -> new NoSuchElementException("no transaction " + transactionId));
    if (suspect.getDuplicateStatus() != DuplicateStatus.SUSPECTED) {
      throw new IllegalStateException(
          "transaction "
              + transactionId
              + " is not awaiting review ("
              + suspect.getDuplicateStatus()
              + ")");
    }
    return suspect;
  }

  /** Presents stored rows to the detector in the shape {@code ledger-core} works in. */
  private static ParsedStatement view(StatementEntity statement, List<TransactionEntity> rows) {
    List<Transaction> domain = rows.stream().map(TransactionEntity::toDomain).toList();
    return new ParsedStatement(
        new StatementId(statement.getId()),
        statement.getBank(),
        statement.getSourceFileName(),
        statement.getContentHash(),
        new DateRange(statement.getPeriodStart(), statement.getPeriodEnd()),
        domain);
  }
}
