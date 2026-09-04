package dev.ledger.app.repo;

import dev.ledger.app.domain.StatementEntity;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StatementRepository extends JpaRepository<StatementEntity, UUID> {

  /** SPEC §5.2: a re-upload of the identical file is caught here, before the parser runs. */
  Optional<StatementEntity> findByUserIdAndContentHash(UUID userId, String contentHash);

  List<StatementEntity> findByUserIdOrderByPeriodEndDesc(UUID userId);

  /**
   * Statements whose covered period overlaps the given one — the only candidates deduplication
   * needs to consider (SPEC §3.4 step 1).
   */
  @Query(
      """
      select s from StatementEntity s
      where s.userId = :userId
        and s.id <> :excludeId
        and s.periodStart <= :end
        and s.periodEnd >= :start
      """)
  List<StatementEntity> findOverlapping(
      @Param("userId") UUID userId,
      @Param("excludeId") UUID excludeId,
      @Param("start") LocalDate start,
      @Param("end") LocalDate end);
}
