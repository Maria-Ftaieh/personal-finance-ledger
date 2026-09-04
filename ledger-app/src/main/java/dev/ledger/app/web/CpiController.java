package dev.ledger.app.web;

import dev.ledger.app.service.CpiService;
import dev.ledger.core.inflation.PriceIndex;
import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** The price index the reports are built on, and the one operation that reaches out to TCMB. */
@RestController
@RequestMapping("/api/cpi")
public class CpiController {

  private final CpiService cpi;

  public CpiController(CpiService cpi) {
    this.cpi = cpi;
  }

  @GetMapping
  public CpiStatus status() {
    PriceIndex index = cpi.index();
    return new CpiStatus(
        index.size(),
        index.earliestMonth().orElse(null),
        index.latestMonth().orElse(null),
        index.levels());
  }

  /**
   * Extends the cache from EVDS.
   *
   * <p>Always 200. A failure to reach TCMB is reported in the body as {@code reached: false}, not
   * as an error status: the application is still working, on seeded data, exactly as SPEC §6.3
   * requires.
   */
  @PostMapping("/refresh")
  public CpiService.RefreshResult refresh() {
    return cpi.refresh();
  }

  /**
   * @param latestPublishedMonth the default base month for every real-terms figure (SPEC §6.4)
   */
  public record CpiStatus(
      int months,
      YearMonth earliestMonth,
      YearMonth latestPublishedMonth,
      Map<YearMonth, BigDecimal> levels) {}
}
