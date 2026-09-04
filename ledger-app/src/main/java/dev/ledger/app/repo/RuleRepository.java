package dev.ledger.app.repo;

import dev.ledger.app.domain.RuleEntity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RuleRepository extends JpaRepository<RuleEntity, UUID> {

  /** Evaluation order: priority, then id, so it is total and stable (SPEC §5.3). */
  List<RuleEntity> findByUserIdOrderByPriorityAscIdAsc(UUID userId);

  Optional<RuleEntity> findByIdAndUserId(UUID id, UUID userId);
}
