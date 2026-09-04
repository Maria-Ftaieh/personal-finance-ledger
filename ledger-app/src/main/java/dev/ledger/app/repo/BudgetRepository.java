package dev.ledger.app.repo;

import dev.ledger.app.domain.BudgetEntity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BudgetRepository extends JpaRepository<BudgetEntity, UUID> {

  List<BudgetEntity> findByUserIdOrderByCategoryIdAsc(UUID userId);

  Optional<BudgetEntity> findByUserIdAndCategoryId(UUID userId, String categoryId);
}
