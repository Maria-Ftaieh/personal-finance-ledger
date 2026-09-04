package dev.ledger.app.inflation;

import java.math.BigDecimal;
import java.time.YearMonth;

/** One month's published index level, as it arrives from EVDS. */
public record CpiPoint(YearMonth month, BigDecimal indexValue) {}
