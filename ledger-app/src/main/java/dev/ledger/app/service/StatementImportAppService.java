package dev.ledger.app.service;

import dev.ledger.app.config.CurrentUser;
import dev.ledger.app.config.DemoProperties;
import dev.ledger.app.domain.StatementEntity;
import dev.ledger.app.domain.TransactionEntity;
import dev.ledger.app.repo.StatementRepository;
import dev.ledger.app.repo.TransactionRepository;
import dev.ledger.core.model.ImportResult;
import dev.ledger.core.model.ParsedStatement;
import dev.ledger.core.model.Transaction;
import dev.ledger.imports.StatementImportService;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Turns an uploaded file into stored, categorised, dedup-checked transactions.
 *
 * <p>The parsing itself is {@code ledger-import}'s job and happens with no Spring involved; this
 * class is the part that needs a database.
 */
@Service
public class StatementImportAppService {

  private final StatementImportService parser;
  private final StatementRepository statements;
  private final TransactionRepository transactions;
  private final CategorisationService categorisation;
  private final DuplicateService duplicates;
  private final BudgetService budgets;
  private final DemoProperties demo;

  public StatementImportAppService(
      StatementRepository statements,
      TransactionRepository transactions,
      CategorisationService categorisation,
      DuplicateService duplicates,
      BudgetService budgets,
      DemoProperties demo) {
    this.parser = new StatementImportService();
    this.statements = statements;
    this.transactions = transactions;
    this.categorisation = categorisation;
    this.duplicates = duplicates;
    this.budgets = budgets;
    this.demo = demo;
  }

  /**
   * @param password the PDF password, or {@code null}. It is passed straight to the parser and is
   *     never stored, logged or echoed back (SPEC §4.3).
   */
  @Transactional
  public ImportOutcome importFile(byte[] content, String fileName, String password) {
    String hash = StatementImportService.sha256(content);

    // SPEC §5.2: an identical re-upload is settled by the hash, before the parser is invoked.
    Optional<StatementEntity> already =
        demo.readOnly()
            ? Optional.empty()
            : statements.findByUserIdAndContentHash(CurrentUser.ID, hash);
    if (already.isPresent()) {
      return ImportOutcome.alreadyImported(already.get().getId());
    }

    ImportResult result = parser.importFile(content, fileName, password);
    return switch (result) {
      case ImportResult.Parsed parsed ->
          demo.readOnly()
              ? ImportOutcome.parsedNotStored(parsed.statement().size())
              : store(parsed.statement(), fileName, hash);
      case ImportResult.NeedsPassword ignored -> ImportOutcome.needsPassword();
      case ImportResult.UnsupportedBank unsupported ->
          ImportOutcome.unsupportedBank(unsupported.detail());
      case ImportResult.Unreadable unreadable -> ImportOutcome.unreadable(unreadable.reason());
    };
  }

  private ImportOutcome store(ParsedStatement parsed, String fileName, String hash) {
    StatementEntity statement =
        new StatementEntity(
            parsed.id().value(),
            CurrentUser.ID,
            parsed.bank(),
            fileName,
            hash,
            parsed.period().start(),
            parsed.period().end());
    statements.save(statement);

    // Identical rows within one file are numbered so that two coffees on the same morning get
    // two fingerprints rather than colliding on the unique constraint. See Fingerprint.
    Map<String, Integer> seen = new HashMap<>();
    List<TransactionEntity> rows = new ArrayList<>(parsed.size());
    for (Transaction row : parsed.transactions()) {
      int occurrence = seen.merge(Fingerprint.rowKey(row), 0, (existing, ignored) -> existing + 1);
      rows.add(
          TransactionEntity.from(
              row, CurrentUser.ID, Fingerprint.of(CurrentUser.ID, hash, occurrence, row)));
    }

    categorisation.apply(categorisation.engine(), rows);
    transactions.saveAll(rows);

    int flagged = duplicates.flagAgainstOverlapping(statement, rows);

    // SPEC §6.5: budgets are evaluated on write. Only the months this file actually touched
    // are re-checked; the rest cannot have changed.
    budgets.evaluate(
        rows.stream()
            .map(row -> YearMonth.from(row.getTransactionDate()))
            .collect(Collectors.toSet()));

    return ImportOutcome.imported(statement.getId(), rows.size(), flagged);
  }

  /** What happened to an upload. One case per outcome the API has to tell the user about. */
  public record ImportOutcome(
      Status status,
      UUID statementId,
      int transactionsImported,
      int suspectedDuplicates,
      String detail) {

    public enum Status {
      IMPORTED,
      /** The same bytes were uploaded before. Nothing changed; this is not an error. */
      ALREADY_IMPORTED,
      /**
       * The file was read correctly and then deliberately discarded, because this deployment is a
       * public demo.
       *
       * <p>Someone will upload a real statement to a public URL — so on a demo the parser runs,
       * reports honestly what it found, and writes nothing. Their financial data never reaches the
       * database, and the demo everyone else is looking at does not change underneath them.
       */
      PARSED_NOT_STORED,
      NEEDS_PASSWORD,
      UNSUPPORTED_BANK,
      UNREADABLE
    }

    static ImportOutcome imported(UUID statementId, int imported, int flagged) {
      return new ImportOutcome(Status.IMPORTED, statementId, imported, flagged, null);
    }

    static ImportOutcome parsedNotStored(int parsed) {
      return new ImportOutcome(
          Status.PARSED_NOT_STORED, null, parsed, 0, "read but not stored: this is a public demo");
    }

    static ImportOutcome alreadyImported(UUID statementId) {
      return new ImportOutcome(
          Status.ALREADY_IMPORTED, statementId, 0, 0, "this file has already been imported");
    }

    static ImportOutcome needsPassword() {
      return new ImportOutcome(Status.NEEDS_PASSWORD, null, 0, 0, "the file is password protected");
    }

    static ImportOutcome unsupportedBank(String detail) {
      return new ImportOutcome(Status.UNSUPPORTED_BANK, null, 0, 0, detail);
    }

    static ImportOutcome unreadable(String detail) {
      return new ImportOutcome(Status.UNREADABLE, null, 0, 0, detail);
    }
  }
}
