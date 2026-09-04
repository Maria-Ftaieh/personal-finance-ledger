package dev.ledger.app.web;

import dev.ledger.app.domain.DuplicateStatus;
import dev.ledger.app.domain.TransactionEntity;
import dev.ledger.core.model.BankCode;
import dev.ledger.core.model.Installment;
import dev.ledger.core.money.Money;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * A transaction as the API presents it.
 *
 * <p>Both the raw and the normalised description are exposed: the raw one is what the user
 * recognises from their statement, and the normalised one is what the rules and the dedup pass
 * actually compared, which is what makes a surprising categorisation explainable.
 *
 * <p>{@link Money} serialises as a string, never a JSON number — see {@code MoneyJsonModule}.
 */
public record TransactionView(
    UUID id,
    UUID statementId,
    LocalDate transactionDate,
    LocalDate postingDate,
    String rawDescription,
    String normalisedDescription,
    Money amount,
    Money originalAmount,
    String installment,
    BankCode bank,
    String categoryId,
    String subcategoryId,
    boolean categoryOverride,
    DuplicateStatus duplicateStatus,
    UUID duplicateOfId,
    BigDecimal duplicateSimilarity,
    String duplicateReason) {

  public static TransactionView of(TransactionEntity entity) {
    Installment installment = entity.getInstallment();
    return new TransactionView(
        entity.getId(),
        entity.getStatementId(),
        entity.getTransactionDate(),
        entity.getPostingDate(),
        entity.getRawDescription(),
        entity.getNormalisedDescription(),
        entity.getAmount(),
        entity.getOriginalAmount(),
        installment == null ? null : installment.toString(),
        entity.getBank(),
        entity.getCategoryId(),
        entity.getSubcategoryId(),
        entity.isCategoryOverride(),
        entity.getDuplicateStatus(),
        entity.getDuplicateOfId(),
        entity.getDuplicateSimilarity(),
        entity.getDuplicateReason());
  }
}
