package dev.ledger.app.repo;

import dev.ledger.app.domain.CpiObservationEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CpiRepository
    extends JpaRepository<CpiObservationEntity, CpiObservationEntity.Key> {

  List<CpiObservationEntity> findBySeriesCodeOrderByMonthAsc(String seriesCode);

  Optional<CpiObservationEntity> findFirstBySeriesCodeOrderByMonthDesc(String seriesCode);
}
