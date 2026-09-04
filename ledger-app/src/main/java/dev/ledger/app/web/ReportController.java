package dev.ledger.app.web;

import dev.ledger.app.service.ReportService;
import java.time.YearMonth;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** SPEC §6.5. Months are {@code yyyy-MM}; every figure carries its base month with it. */
@RestController
@RequestMapping("/api/reports")
public class ReportController {

  private final ReportService reports;

  public ReportController(ReportService reports) {
    this.reports = reports;
  }

  /**
   * @param baseMonth optional; defaults to the most recent month with a published CPI (SPEC §6.4)
   */
  @GetMapping("/monthly")
  public ReportService.MonthlyReport monthly(
      @RequestParam @DateTimeFormat(pattern = "yyyy-MM") YearMonth month,
      @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM") YearMonth baseMonth) {
    return reports.monthly(month, baseMonth);
  }

  /** The headline: the same month, this year against last, in real terms. */
  @GetMapping("/year-over-year")
  public ReportService.YearOverYearReport yearOverYear(
      @RequestParam @DateTimeFormat(pattern = "yyyy-MM") YearMonth month,
      @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM") YearMonth baseMonth) {
    return reports.yearOverYear(month, baseMonth);
  }

  /** Months that actually hold transactions, so the UI offers only real choices. */
  @GetMapping("/months")
  public List<YearMonth> months() {
    return reports.monthsWithSpending();
  }
}
