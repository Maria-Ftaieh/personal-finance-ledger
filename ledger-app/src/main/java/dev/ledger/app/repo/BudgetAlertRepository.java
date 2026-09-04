package dev.ledger.app.repo;

import dev.ledger.app.domain.BudgetAlertEntity;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BudgetAlertRepository extends JpaRepository<BudgetAlertEntity, UUID> {

  List<BudgetAlertEntity> findByUserIdOrderByMonthDescCategoryIdAsc(UUID userId);

  List<BudgetAlertEntity> findByUserIdAndMonthOrderByCategoryIdAsc(UUID userId, LocalDate month);

  Optional<BudgetAlertEntity> findByUserIdAndCategoryIdAndMonth(
      UUID userId, String categoryId, LocalDate month);
}
