package dev.ledger.app.domain;

import dev.ledger.core.model.BankCode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "statements")
public class StatementEntity {

  @Id private UUID id;

  @Column(name = "user_id", nullable = false)
  private UUID userId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 32)
  private BankCode bank;

  @Column(name = "source_file_name", nullable = false, length = 512)
  private String sourceFileName;

  @Column(name = "content_hash", nullable = false, length = 64)
  private String contentHash;

  @Column(name = "period_start", nullable = false)
  private LocalDate periodStart;

  @Column(name = "period_end", nullable = false)
  private LocalDate periodEnd;

  @Column(name = "imported_at", nullable = false, insertable = false, updatable = false)
  private Instant importedAt;

  protected StatementEntity() {}

  public StatementEntity(
      UUID id,
      UUID userId,
      BankCode bank,
      String sourceFileName,
      String contentHash,
      LocalDate periodStart,
      LocalDate periodEnd) {
    this.id = id;
    this.userId = userId;
    this.bank = bank;
    this.sourceFileName = sourceFileName;
    this.contentHash = contentHash;
    this.periodStart = periodStart;
    this.periodEnd = periodEnd;
  }

  public UUID getId() {
    return id;
  }

  public UUID getUserId() {
    return userId;
  }

  public BankCode getBank() {
    return bank;
  }

  public String getSourceFileName() {
    return sourceFileName;
  }

  public String getContentHash() {
    return contentHash;
  }

  public LocalDate getPeriodStart() {
    return periodStart;
  }

  public LocalDate getPeriodEnd() {
    return periodEnd;
  }

  public Instant getImportedAt() {
    return importedAt;
  }
}
