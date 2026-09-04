package dev.ledger.app.inflation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The EVDS client against a stub serving the exact payload the live service returned on 2026-09-04.
 *
 * <p>Hermetic on purpose: this asserts the request the client builds and the parsing of the reply,
 * neither of which should depend on TCMB being up or on the network existing. What it cannot check
 * is that the real endpoint still behaves this way — that was established by calling it, and the
 * shape is recorded here so a change in it fails loudly rather than silently returning nothing.
 */
class EvdsClientTest {

  /** Trimmed from a real response. Note the series code with underscores, and string values. */
  private static final String REAL_RESPONSE =
      """
      {"totalCount":4,"items":[
        {"Tarih":"05-2026","TP_GENENDEKS_T1":"4056.05","UNIXTIME":{"$numberLong":"1777561200"}},
        {"Tarih":"06-2026","TP_GENENDEKS_T1":"4137.93","UNIXTIME":{"$numberLong":"1780239600"}},
        {"Tarih":"07-2026","TP_GENENDEKS_T1":"4211.58","UNIXTIME":{"$numberLong":"1782831600"}},
        {"Tarih":"08-2026","TP_GENENDEKS_T1":"4289.23","UNIXTIME":{"$numberLong":"1785510000"}}]}
      """;

  private HttpServer server;
  private final List<String> bodies = new ArrayList<>();
  private final Map<String, String> lastHeaders = new ConcurrentHashMap<>();
  private volatile int status = 200;
  private volatile String payload = REAL_RESPONSE;

  @BeforeEach
  void startStub() throws IOException {
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext(
        "/fe",
        exchange -> {
          try (InputStream in = exchange.getRequestBody()) {
            bodies.add(new String(in.readAllBytes(), StandardCharsets.UTF_8));
          }
          exchange
              .getRequestHeaders()
              .forEach((k, v) -> lastHeaders.put(k.toLowerCase(java.util.Locale.ROOT), v.get(0)));
          byte[] out = payload.getBytes(StandardCharsets.UTF_8);
          exchange.sendResponseHeaders(status, out.length);
          exchange.getResponseBody().write(out);
          exchange.close();
        });
    server.start();
  }

  @AfterEach
  void stopStub() {
    server.stop(0);
  }

  private EvdsClient client(String apiKey) {
    return new EvdsClient(
        new EvdsProperties(
            "http://127.0.0.1:" + server.getAddress().getPort() + "/fe",
            "TP.GENENDEKS.T1",
            apiKey,
            true,
            Duration.ofSeconds(5)));
  }

  @Test
  @DisplayName("published months are parsed as exact decimals")
  void parsesTheResponse() {
    List<CpiPoint> points = client(null).fetchMonthly(YearMonth.of(2026, 5), YearMonth.of(2026, 9));

    assertThat(points)
        .containsExactly(
            new CpiPoint(YearMonth.of(2026, 5), new BigDecimal("4056.05")),
            new CpiPoint(YearMonth.of(2026, 6), new BigDecimal("4137.93")),
            new CpiPoint(YearMonth.of(2026, 7), new BigDecimal("4211.58")),
            new CpiPoint(YearMonth.of(2026, 8), new BigDecimal("4289.23")));
  }

  @Test
  @DisplayName("the request is a POST with dd-MM-yyyy dates and the monthly frequency")
  void buildsTheRequestTheServiceExpects() {
    client(null).fetchMonthly(YearMonth.of(2026, 5), YearMonth.of(2026, 9));

    assertThat(bodies).hasSize(1);
    assertThat(bodies.get(0))
        .contains("\"series\":\"TP.GENENDEKS.T1\"")
        .contains("\"startDate\":\"01-05-2026\"")
        .contains("\"endDate\":\"30-09-2026\"")
        .contains("\"frequency\":\"5\"")
        .contains("\"decimalSeperator\":\".\"");
  }

  @Test
  @DisplayName("SPEC §6.1: the key goes in a header, never in the URL")
  void sendsTheKeyAsAHeader() {
    client("SECRET-KEY").fetchMonthly(YearMonth.of(2026, 8), YearMonth.of(2026, 8));

    assertThat(lastHeaders).containsEntry("key", "SECRET-KEY");
    assertThat(bodies.get(0)).doesNotContain("SECRET-KEY");
  }

  @Test
  @DisplayName("no key configured means no key header, and it still works")
  void worksWithoutAKey() {
    assertThat(client(" ").fetchMonthly(YearMonth.of(2026, 8), YearMonth.of(2026, 8))).isNotEmpty();

    assertThat(lastHeaders).doesNotContainKey("key");
  }

  @Test
  @DisplayName("a month EVDS lists but has not published is skipped, never invented")
  void skipsUnpublishedMonths() {
    payload =
        """
        {"totalCount":2,"items":[
          {"Tarih":"08-2026","TP_GENENDEKS_T1":"4289.23"},
          {"Tarih":"09-2026","TP_GENENDEKS_T1":null}]}
        """;

    assertThat(client(null).fetchMonthly(YearMonth.of(2026, 8), YearMonth.of(2026, 9)))
        .containsExactly(new CpiPoint(YearMonth.of(2026, 8), new BigDecimal("4289.23")));
  }

  @Test
  @DisplayName("an error status is an unavailability, not a parse attempt")
  void reportsErrorStatuses() {
    status = 503;

    assertThatThrownBy(
            () -> client(null).fetchMonthly(YearMonth.of(2026, 8), YearMonth.of(2026, 8)))
        .isInstanceOf(EvdsUnavailableException.class)
        .hasMessageContaining("503");
  }

  @Test
  @DisplayName("an unreadable body is an unavailability rather than an empty result")
  void reportsMalformedBodies() {
    payload = "<html>Sayfa Goruntulenemedi</html>";

    assertThatThrownBy(
            () -> client(null).fetchMonthly(YearMonth.of(2026, 8), YearMonth.of(2026, 8)))
        .isInstanceOf(EvdsUnavailableException.class);
  }

  @Test
  @DisplayName("an unreachable service fails as unavailable, so the caller can fall back")
  void reportsUnreachableService() {
    EvdsClient unreachable =
        new EvdsClient(
            new EvdsProperties(
                "http://127.0.0.1:1/fe", "TP.GENENDEKS.T1", null, true, Duration.ofSeconds(2)));

    assertThatThrownBy(() -> unreachable.fetchMonthly(YearMonth.of(2026, 8), YearMonth.of(2026, 8)))
        .isInstanceOf(EvdsUnavailableException.class);
  }

  @Test
  @DisplayName("disabled means the network is never touched at all")
  void honoursTheDisabledSwitch() {
    EvdsClient disabled =
        new EvdsClient(
            new EvdsProperties(
                "http://127.0.0.1:" + server.getAddress().getPort() + "/fe",
                "TP.GENENDEKS.T1",
                null,
                false,
                Duration.ofSeconds(5)));

    assertThatThrownBy(() -> disabled.fetchMonthly(YearMonth.of(2026, 8), YearMonth.of(2026, 8)))
        .isInstanceOf(EvdsUnavailableException.class)
        .hasMessageContaining("disabled");
    assertThat(bodies).isEmpty();
  }
}
