package dev.ledger.app.inflation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Reads CPI observations from TCMB's EVDS.
 *
 * <p>SPEC §6.1 says to verify the endpoint empirically before writing the client, because every
 * snippet predating 2026 targets {@code evds2.tcmb.gov.tr/service/evds/?key=...} which now
 * redirects to a single-page app. What is implemented here is what the service actually does, as of
 * 2026-09-04:
 *
 * <ul>
 *   <li>{@code POST https://evds3.tcmb.gov.tr/igmevdsms-dis/fe} with a JSON body. Not a GET, and
 *       not the old path-embedded query string.
 *   <li>Dates go in as {@code dd-MM-yyyy}; {@code frequency: "5"} is monthly.
 *   <li>The response is {@code
 *       {"totalCount":n,"items":[{"Tarih":"MM-yyyy","TP_GENENDEKS_T1":"4289.23"}]}} — the series
 *       code with its dots turned into underscores, and the value as a string. Only published
 *       months come back; there is no null padding to the requested end date.
 *   <li>The service answered without any credential. The key is still sent as a <b>header</b> when
 *       one is configured, per §6.1, so that a future requirement is a configuration change rather
 *       than a code change; it is never put in the URL.
 * </ul>
 *
 * <p>On TLS: §6.1 warns TCMB's configuration may be too old for the JDK's defaults. Measured, the
 * handshake negotiates TLSv1.2 with {@code TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256} and the default
 * {@link HttpClient} accepts it, so no custom {@code SSLContext} is configured. If that ever
 * changes, the fix is an explicit context here — never disabled verification.
 */
@Component
public class EvdsClient {

  private static final Logger log = LoggerFactory.getLogger(EvdsClient.class);

  /** EVDS frequency code for monthly series. */
  private static final String MONTHLY = "5";

  private static final DateTimeFormatter EVDS_DATE =
      DateTimeFormatter.ofPattern("dd-MM-yyyy", Locale.ROOT);

  private static final DateTimeFormatter EVDS_MONTH =
      DateTimeFormatter.ofPattern("MM-yyyy", Locale.ROOT);

  private final EvdsProperties properties;
  private final HttpClient http;
  private final ObjectMapper json = new ObjectMapper();

  public EvdsClient(EvdsProperties properties) {
    this.properties = properties;
    this.http = HttpClient.newBuilder().connectTimeout(properties.timeout()).build();
  }

  public boolean isEnabled() {
    return properties.enabled();
  }

  public String seriesCode() {
    return properties.seriesCode();
  }

  /**
   * Fetches every published month in the inclusive range.
   *
   * @throws EvdsUnavailableException on any transport, status or parse failure — the caller falls
   *     back to what it has cached rather than failing the request
   */
  public List<CpiPoint> fetchMonthly(YearMonth from, YearMonth to) {
    if (!properties.enabled()) {
      throw new EvdsUnavailableException("EVDS access is disabled by configuration");
    }
    HttpRequest.Builder request =
        HttpRequest.newBuilder(URI.create(properties.baseUrl()))
            .header("Content-Type", "application/json")
            .header("Accept", "application/json")
            .timeout(properties.timeout())
            .POST(HttpRequest.BodyPublishers.ofString(requestBody(from, to)));

    if (properties.hasApiKey()) {
      request.header("key", properties.apiKey());
    }

    HttpResponse<String> response;
    try {
      response = http.send(request.build(), HttpResponse.BodyHandlers.ofString());
    } catch (IOException e) {
      throw new EvdsUnavailableException("could not reach EVDS: " + e.getMessage(), e);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new EvdsUnavailableException("interrupted while calling EVDS", e);
    }

    if (response.statusCode() != 200) {
      throw new EvdsUnavailableException("EVDS answered " + response.statusCode());
    }
    return parse(response.body());
  }

  private String requestBody(YearMonth from, YearMonth to) {
    return """
        {"type":"json","series":"%s","aggregationTypes":"avg","formulas":"0",\
        "startDate":"%s","endDate":"%s","frequency":"%s",\
        "decimalSeperator":".","decimal":"6","dateFormat":"1","lang":"EN",\
        "yon":"","sira":"","ozelFormuller":[],"groupSeperator":false,"isRaporSayfasi":false}"""
        .formatted(
            properties.seriesCode(),
            from.atDay(1).format(EVDS_DATE),
            to.atEndOfMonth().format(EVDS_DATE),
            MONTHLY);
  }

  private List<CpiPoint> parse(String body) {
    // The response names each series by its code with dots replaced by underscores.
    String field = properties.seriesCode().replace('.', '_');
    List<CpiPoint> points = new ArrayList<>();
    try {
      JsonNode items = json.readTree(body).path("items");
      for (JsonNode item : items) {
        JsonNode value = item.path(field);
        JsonNode month = item.path("Tarih");
        if (value.isMissingNode() || value.isNull() || month.isMissingNode()) {
          continue; // a month EVDS lists but has not published; never invented (SPEC §6.2)
        }
        String raw = value.asText().trim();
        if (raw.isEmpty()) {
          continue;
        }
        points.add(new CpiPoint(YearMonth.parse(month.asText(), EVDS_MONTH), new BigDecimal(raw)));
      }
    } catch (RuntimeException | com.fasterxml.jackson.core.JsonProcessingException e) {
      throw new EvdsUnavailableException("could not read the EVDS response: " + e.getMessage(), e);
    }
    log.debug("EVDS returned {} published points for {}", points.size(), properties.seriesCode());
    return points;
  }
}
