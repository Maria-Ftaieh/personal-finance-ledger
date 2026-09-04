package dev.ledger.app.service;

import dev.ledger.app.domain.CpiObservationEntity;
import dev.ledger.app.inflation.CpiPoint;
import dev.ledger.app.inflation.EvdsClient;
import dev.ledger.app.inflation.EvdsUnavailableException;
import dev.ledger.app.repo.CpiRepository;
import dev.ledger.core.inflation.PriceIndex;
import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The application's view of the consumer price index: seeded history, extended from EVDS on
 * request, always served from the local table.
 *
 * <p>SPEC §6.3. Reports never call EVDS. They read the cache, which the Flyway seed fills with
 * twenty-three years of published levels, so the application produces real-spending figures with no
 * network and no API key. {@link #refresh()} is the only thing that reaches out, and it is
 * explicitly invoked.
 */
@Service
public class CpiService {

  private static final Logger log = LoggerFactory.getLogger(CpiService.class);

  private final CpiRepository observations;
  private final EvdsClient evds;

  public CpiService(CpiRepository observations, EvdsClient evds) {
    this.observations = observations;
    this.evds = evds;
  }

  /** Everything cached for the configured series, as the domain's {@link PriceIndex}. */
  @Transactional(readOnly = true)
  public PriceIndex index() {
    Map<YearMonth, BigDecimal> levels = new LinkedHashMap<>();
    for (CpiObservationEntity row :
        observations.findBySeriesCodeOrderByMonthAsc(evds.seriesCode())) {
      levels.put(row.getMonth(), row.getIndexValue());
    }
    return PriceIndex.of(levels);
  }

  /**
   * The default base month: the most recent month with a published level.
   *
   * <p>SPEC §6.4. Not "last month" — CPI is published in the first days of the following month and
   * a release can slip, so the only honest answer is what is actually cached.
   */
  @Transactional(readOnly = true)
  public Optional<YearMonth> latestPublishedMonth() {
    return observations
        .findFirstBySeriesCodeOrderByMonthDesc(evds.seriesCode())
        .map(CpiObservationEntity::getMonth);
  }

  /**
   * Extends the cache from EVDS.
   *
   * <p>SPEC §6.2 says to fetch only gaps. The window starts at the latest month already cached
   * rather than the one after it: re-reading that single month is what makes a TÜİK revision
   * visible, since the stored value and its fetch timestamp are then compared against a fresh
   * reading. Everything earlier is settled and is never requested again.
   */
  @Transactional
  public RefreshResult refresh() {
    String series = evds.seriesCode();
    YearMonth from = latestPublishedMonth().orElse(YearMonth.of(2003, 1));
    YearMonth to = YearMonth.now();
    if (from.isAfter(to)) {
      to = from;
    }

    List<CpiPoint> fetched;
    try {
      fetched = evds.fetchMonthly(from, to);
    } catch (EvdsUnavailableException e) {
      // Not an error worth failing a request over: the seeded history still answers reports.
      log.warn("EVDS refresh failed, serving cached CPI only: {}", e.getMessage());
      return new RefreshResult(
          series, 0, 0, latestPublishedMonth().orElse(null), false, e.getMessage());
    }

    int added = 0;
    int revised = 0;
    for (CpiPoint point : fetched) {
      Optional<CpiObservationEntity> existing =
          observations.findById(new CpiObservationEntity.Key(series, point.month().atDay(1)));
      if (existing.isPresent()) {
        if (existing.get().refresh(point.indexValue(), "EVDS")) {
          revised++;
          log.info("TÜİK revised {} for {}: now {}", series, point.month(), point.indexValue());
        }
        observations.save(existing.get());
      } else {
        observations.save(
            new CpiObservationEntity(series, point.month(), point.indexValue(), "EVDS"));
        added++;
      }
    }

    return new RefreshResult(
        series, added, revised, latestPublishedMonth().orElse(null), true, null);
  }

  /**
   * @param reached whether EVDS answered. False means the cache is unchanged and still usable.
   * @param detail why it did not answer, when it did not
   */
  public record RefreshResult(
      String seriesCode,
      int added,
      int revised,
      YearMonth latestPublishedMonth,
      boolean reached,
      String detail) {}
}
