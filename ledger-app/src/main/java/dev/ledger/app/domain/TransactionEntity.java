package dev.ledger.app.domain;

import dev.ledger.core.model.BankCode;
import dev.ledger.core.model.Installment;
import dev.ledger.core.model.StatementId;
import dev.ledger.core.model.Transaction;
import dev.ledger.core.model.TransactionId;
import dev.ledger.core.money.Money;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Currency;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * A persisted statement line.
 *
 * <p>Associations are deliberately absent: the statement, category and subcategory are held as
 * plain id columns with the foreign keys enforced by the database. Nothing here needs to navigate
 * an object graph, and a lazy association that has to be initialised is a source of bugs a report
 * query does not need.
 */
@Entity
@Table(name = "transactions")
public class TransactionEntity {

  @Id private UUID id;

  @Column(name = "user_id", nullable = false)
  private UUID userId;

  @Column(name = "statement_id", nullable = false)
  private UUID statementId;

  @Column(name = "transaction_date", nullable = false)
  private LocalDate transactionDate;

  @Column(name = "posting_date", nullable = false)
  private LocalDate postingDate;

  @Column(name = "raw_description", nullable = false)
  private String rawDescription;

  @Column(name = "normalised_description", nullable = false)
  private String normalisedDescription;

  @Column(nullable = false, precision = 19, scale = 4)
  private BigDecimal amount;

  // SPEC §3.1 asks for a char(3) currency column; Hibernate maps String to varchar unless
  // told otherwise, and ddl-auto: validate rejects the mismatch on startup.
  @JdbcTypeCode(SqlTypes.CHAR)
  @Column(nullable = false, length = 3, columnDefinition = "char(3)")
  private String currency;

  @Column(name = "original_amount", precision = 19, scale = 4)
  private BigDecimal originalAmount;

  @JdbcTypeCode(SqlTypes.CHAR)
  @Column(name = "original_currency", length = 3, columnDefinition = "char(3)")
  private String originalCurrency;

  @Column(name = "installment_current")
  private Integer installmentCurrent;

  @Column(name = "installment_total")
  private Integer installmentTotal;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 32)
  private BankCode bank;

  @Column(nullable = false, length = 64)
  private String fingerprint;

  @Column(name = "category_id", length = 64)
  private String categoryId;

  @Column(name = "subcategory_id", length = 128)
  private String subcategoryId;

  @Column(name = "category_override", nullable = false)
  private boolean categoryOverride;

  @Enumerated(EnumType.STRING)
  @Column(name = "duplicate_status", nullable = false, length = 16)
  private DuplicateStatus duplicateStatus = DuplicateStatus.NONE;

  @Column(name = "duplicate_of_id")
  private UUID duplicateOfId;

  @Column(name = "duplicate_similarity", precision = 5, scale = 4)
  private BigDecimal duplicateSimilarity;

  @Column(name = "duplicate_reason", length = 32)
  private String duplicateReason;

  protected TransactionEntity() {}

  /** Builds a row from a parsed transaction. The fingerprint is supplied by the import service. */
  public static TransactionEntity from(Transaction source, UUID userId, String fingerprint) {
    TransactionEntity entity = new TransactionEntity();
    entity.id = source.id().value();
    entity.userId = userId;
    entity.statementId = source.sourceStatement().value();
    entity.transactionDate = source.transactionDate();
    entity.postingDate = source.postingDate();
    entity.rawDescription = source.rawDescription();
    entity.normalisedDescription = source.normalisedDescription();
    entity.amount = source.amount().amount();
    entity.currency = source.amount().currency().getCurrencyCode();
    if (source.originalAmount() != null) {
      entity.originalAmount = source.originalAmount().amount();
      entity.originalCurrency = source.originalAmount().currency().getCurrencyCode();
    }
    if (source.installment() != null) {
      entity.installmentCurrent = source.installment().current();
      entity.installmentTotal = source.installment().total();
    }
    entity.bank = source.bank();
    entity.fingerprint = fingerprint;
    return entity;
  }

  /** Back to the domain type, so core logic never has to know about JPA. */
  public Transaction toDomain() {
    return new Transaction(
        new TransactionId(id),
        transactionDate,
        postingDate,
        rawDescription,
        normalisedDescription,
        getAmount(),
        getOriginalAmount(),
        getInstallment(),
        bank,
        new StatementId(statementId));
  }

  public Money getAmount() {
    return Money.of(amount, Currency.getInstance(currency));
  }

  public Money getOriginalAmount() {
    return originalAmount == null
        ? null
        : Money.of(originalAmount, Currency.getInstance(originalCurrency));
  }

  public Installment getInstallment() {
    return installmentTotal == null ? null : new Installment(installmentCurrent, installmentTotal);
  }

  /** Applies a rule's verdict. Refuses to touch a category the user set by hand (SPEC §5.3). */
  public boolean applyCategory(String category, String subcategory) {
    if (categoryOverride) {
      return false;
    }
    boolean changed =
        !java.util.Objects.equals(categoryId, category)
            || !java.util.Objects.equals(subcategoryId, subcategory);
    this.categoryId = category;
    this.subcategoryId = subcategory;
    return changed;
  }

  /** A category the user chose. Survives every later rule re-evaluation. */
  public void overrideCategory(String category, String subcategory) {
    this.categoryId = category;
    this.subcategoryId = subcategory;
    this.categoryOverride = true;
  }

  public void clearCategoryOverride() {
    this.categoryOverride = false;
  }

  public void flagAsSuspectedDuplicate(UUID originalId, BigDecimal similarity, String reason) {
    this.duplicateStatus = DuplicateStatus.SUSPECTED;
    this.duplicateOfId = originalId;
    this.duplicateSimilarity = similarity;
    this.duplicateReason = reason;
  }

  public void confirmDuplicate() {
    this.duplicateStatus = DuplicateStatus.CONFIRMED;
  }

  /** The user says it is a genuine separate purchase; the pointer is kept as an audit trail. */
  public void rejectDuplicate() {
    this.duplicateStatus = DuplicateStatus.REJECTED;
  }

  public UUID getId() {
    return id;
  }

  public UUID getUserId() {
    return userId;
  }

  public UUID getStatementId() {
    return statementId;
  }

  public LocalDate getTransactionDate() {
    return transactionDate;
  }

  public LocalDate getPostingDate() {
    return postingDate;
  }

  public String getRawDescription() {
    return rawDescription;
  }

  public String getNormalisedDescription() {
    return normalisedDescription;
  }

  public BankCode getBank() {
    return bank;
  }

  public String getFingerprint() {
    return fingerprint;
  }

  public String getCategoryId() {
    return categoryId;
  }

  public String getSubcategoryId() {
    return subcategoryId;
  }

  public boolean isCategoryOverride() {
    return categoryOverride;
  }

  public DuplicateStatus getDuplicateStatus() {
    return duplicateStatus;
  }

  public UUID getDuplicateOfId() {
    return duplicateOfId;
  }

  public BigDecimal getDuplicateSimilarity() {
    return duplicateSimilarity;
  }

  public String getDuplicateReason() {
    return duplicateReason;
  }
}
