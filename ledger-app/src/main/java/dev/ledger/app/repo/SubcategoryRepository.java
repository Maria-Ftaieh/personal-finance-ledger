package dev.ledger.app.repo;

import dev.ledger.app.domain.SubcategoryEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SubcategoryRepository extends JpaRepository<SubcategoryEntity, String> {

  List<SubcategoryEntity> findAllByOrderByCategoryIdAscSortOrderAsc();
}
