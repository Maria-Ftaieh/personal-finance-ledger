package dev.ledger.core.model;

import dev.ledger.core.money.Money;
import dev.ledger.core.text.DescriptionNormalizer;
import java.time.LocalDate;
import java.util.Objects;

/**
 * One line on a statement.
 *
 * <p>SPEC §3.3. {@code rawDescription} is immutable and preserved exactly as the bank printed it.
 * Everything derived from it — {@code normalisedDescription}, the dedup fingerprint, the assigned
 * category — is recomputable from that text alone. That is what allows a later, better normaliser
 * or rule set to be applied to years of history without asking the user to re-upload anything.
 *
 * @param transactionDate when the purchase happened
 * @param postingDate when the bank booked it; frequently the same day, sometimes later
 * @param rawDescription exactly as printed, never modified
 * @param normalisedDescription derived from {@code rawDescription}, used for matching only
 * @param amount charged in the statement's currency; negative for a refund
 * @param originalAmount the foreign amount for an FX purchase, otherwise {@code null}
 * @param installment the {@code 3/8} marker, otherwise {@code null}
 */
public record Transaction(
    TransactionId id,
    LocalDate transactionDate,
    LocalDate postingDate,
    String rawDescription,
    String normalisedDescription,
    Money amount,
    Money originalAmount,
    Installment installment,
    BankCode bank,
    StatementId sourceStatement) {

  public Transaction {
    Objects.requireNonNull(id, "id");
    Objects.requireNonNull(transactionDate, "transactionDate");
    Objects.requireNonNull(postingDate, "postingDate");
    Objects.requireNonNull(rawDescription, "rawDescription");
    Objects.requireNonNull(normalisedDescription, "normalisedDescription");
    Objects.requireNonNull(amount, "amount");
    Objects.requireNonNull(bank, "bank");
    Objects.requireNonNull(sourceStatement, "sourceStatement");
    if (originalAmount != null && originalAmount.currency().equals(amount.currency())) {
      throw new IllegalArgumentException(
          "originalAmount is for foreign currency purchases; it must not repeat "
              + amount.currency().getCurrencyCode());
    }
  }

  /** The only way to build a transaction: the normalised form is derived, never supplied. */
  public static Transaction create(
      TransactionId id,
      LocalDate transactionDate,
      LocalDate postingDate,
      String rawDescription,
      Money amount,
      Money originalAmount,
      Installment installment,
      BankCode bank,
      StatementId sourceStatement) {
    return new Transaction(
        id,
        transactionDate,
        postingDate,
        rawDescription,
        DescriptionNormalizer.normalise(rawDescription),
        amount,
        originalAmount,
        installment,
        bank,
        sourceStatement);
  }

  /** Re-derives the matching form after {@link DescriptionNormalizer} changes. */
  public Transaction withRecomputedNormalisation() {
    return new Transaction(
        id,
        transactionDate,
        postingDate,
        rawDescription,
        DescriptionNormalizer.normalise(rawDescription),
        amount,
        originalAmount,
        installment,
        bank,
        sourceStatement);
  }

  public boolean isRefund() {
    return amount.isNegative();
  }

  public boolean isForeignCurrency() {
    return originalAmount != null;
  }

  public boolean isInstallment() {
    return installment != null;
  }
}
