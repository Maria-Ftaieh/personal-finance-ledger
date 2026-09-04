package dev.ledger.app.repo;

import dev.ledger.app.domain.DuplicateStatus;
import dev.ledger.app.domain.TransactionEntity;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TransactionRepository extends JpaRepository<TransactionEntity, UUID> {

  List<TransactionEntity> findByStatementIdOrderByTransactionDateAscIdAsc(UUID statementId);

  List<TransactionEntity> findByStatementIdInOrderByTransactionDateAscIdAsc(
      Collection<UUID> statementIds);

  List<TransactionEntity> findByUserIdAndDuplicateStatusOrderByTransactionDateDesc(
      UUID userId, DuplicateStatus status);

  /**
   * The report and table query. Every filter is optional; the leading {@code userId} and date range
   * are what the {@code (user_id, transaction_date)} index is for (SPEC §5.2).
   */
  @Query(
      """
      select t from TransactionEntity t
      where t.userId = :userId
        and (cast(:from as localdate) is null or t.transactionDate >= :from)
        and (cast(:to as localdate) is null or t.transactionDate <= :to)
        and (:categoryId is null or t.categoryId = :categoryId)
        and (:includeConfirmedDuplicates = true or t.duplicateStatus <> dev.ledger.app.domain.DuplicateStatus.CONFIRMED)
      order by t.transactionDate desc, t.id asc
      """)
  List<TransactionEntity> search(
      @Param("userId") UUID userId,
      @Param("from") LocalDate from,
      @Param("to") LocalDate to,
      @Param("categoryId") String categoryId,
      @Param("includeConfirmedDuplicates") boolean includeConfirmedDuplicates);

  /** Everything a rule re-evaluation may touch: a manual override is excluded by the caller. */
  List<TransactionEntity> findByUserId(UUID userId);

  /**
   * Spending in one month, grouped for the report. A confirmed duplicate is excluded: it is still
   * on the record, but it is not part of what the user spent (SPEC §3.4).
   *
   * <p>Refunds are negative amounts and are summed in, so they reduce their category's total
   * exactly as SPEC §4.3 requires.
   */
  @Query(
      """
      select t.categoryId, t.subcategoryId, t.currency, sum(t.amount)
      from TransactionEntity t
      where t.userId = :userId
        and t.transactionDate >= :from
        and t.transactionDate <= :to
        and t.duplicateStatus <> dev.ledger.app.domain.DuplicateStatus.CONFIRMED
      group by t.categoryId, t.subcategoryId, t.currency
      """)
  List<Object[]> sumByCategory(
      @Param("userId") UUID userId, @Param("from") LocalDate from, @Param("to") LocalDate to);

  /** The months that actually hold transactions, so a report can offer only real ones. */
  @Query(
      """
      select distinct t.transactionDate from TransactionEntity t where t.userId = :userId
      """)
  List<LocalDate> distinctTransactionDates(@Param("userId") UUID userId);
}
