package dev.ledger.app.inflation;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * How to reach TCMB's EVDS service.
 *
 * @param baseUrl the observations endpoint. SPEC §6.1 warns that the old {@code
 *     evds2.tcmb.gov.tr/service/evds/} address is dead; checked on 2026-09-04 it answers 302 to an
 *     SPA. The value below was found by reading what the current EVDS web application itself calls,
 *     and verified to return data.
 * @param seriesCode the CPI general index series. See {@code V5__Seed_cpi_history} for why this is
 *     not the {@code TP.FG.J0} the spec names.
 * @param apiKey from {@code EVDS_API_KEY}, sent as a header and never as a query parameter (SPEC
 *     §6.1). Empty is normal and supported: the endpoint currently serves this series without one,
 *     and the application works from seeded data regardless.
 * @param enabled set false to keep the application entirely offline
 */
@ConfigurationProperties("ledger.evds")
public record EvdsProperties(
    String baseUrl, String seriesCode, String apiKey, boolean enabled, Duration timeout) {

  public boolean hasApiKey() {
    return apiKey != null && !apiKey.isBlank();
  }
}
