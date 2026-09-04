package dev.ledger.app.repo;

import dev.ledger.app.domain.CategoryEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CategoryRepository extends JpaRepository<CategoryEntity, String> {

  List<CategoryEntity> findAllByOrderBySortOrderAsc();
}
