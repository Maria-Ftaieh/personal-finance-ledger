package dev.ledger.app.web;

import dev.ledger.app.config.CurrentUser;
import dev.ledger.app.domain.StatementEntity;
import dev.ledger.app.repo.StatementRepository;
import dev.ledger.app.service.StatementImportAppService;
import dev.ledger.app.service.StatementImportAppService.ImportOutcome;
import dev.ledger.core.model.BankCode;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/statements")
public class StatementController {

  private final StatementImportAppService importer;
  private final StatementRepository statements;

  public StatementController(StatementImportAppService importer, StatementRepository statements) {
    this.importer = importer;
    this.statements = statements;
  }

  /**
   * Uploads a statement.
   *
   * <p>The password, when the PDF needs one, is a request parameter that is handed straight to the
   * parser and never stored or logged (SPEC §4.3).
   *
   * <p>Everything except a successful import and an identical re-upload comes back as 422 with a
   * {@code status} the client switches on — a password prompt, an offer to upload CSV instead, or
   * an explanation that the file is a scan.
   */
  @PostMapping(consumes = "multipart/form-data")
  public ResponseEntity<ImportOutcome> upload(
      @RequestPart("file") MultipartFile file,
      @RequestParam(value = "password", required = false) String password) {

    byte[] content = read(file);
    ImportOutcome outcome = importer.importFile(content, originalName(file), emptyToNull(password));

    HttpStatus status =
        switch (outcome.status()) {
          case IMPORTED -> HttpStatus.CREATED;
          case ALREADY_IMPORTED -> HttpStatus.OK;
          case NEEDS_PASSWORD, UNSUPPORTED_BANK, UNREADABLE -> HttpStatus.UNPROCESSABLE_ENTITY;
        };
    return ResponseEntity.status(status).body(outcome);
  }

  @GetMapping
  public List<StatementView> list() {
    return statements.findByUserIdOrderByPeriodEndDesc(CurrentUser.ID).stream()
        .map(StatementView::of)
        .toList();
  }

  private static byte[] read(MultipartFile file) {
    try {
      return file.getBytes();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private static String originalName(MultipartFile file) {
    String name = file.getOriginalFilename();
    return name == null || name.isBlank() ? "upload" : name;
  }

  private static String emptyToNull(String value) {
    return value == null || value.isBlank() ? null : value;
  }

  public record StatementView(
      UUID id,
      BankCode bank,
      String sourceFileName,
      String contentHash,
      LocalDate periodStart,
      LocalDate periodEnd,
      Instant importedAt) {

    static StatementView of(StatementEntity entity) {
      return new StatementView(
          entity.getId(),
          entity.getBank(),
          entity.getSourceFileName(),
          entity.getContentHash(),
          entity.getPeriodStart(),
          entity.getPeriodEnd(),
          entity.getImportedAt());
    }
  }
}
