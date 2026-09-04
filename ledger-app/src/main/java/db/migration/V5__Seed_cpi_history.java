package db.migration;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.sql.PreparedStatement;
import java.time.LocalDate;
import java.time.YearMonth;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

/**
 * Seeds the checked-in CPI history.
 *
 * <p>SPEC §6.3: the application must produce real-spending figures with no network access and no
 * API key, and only call EVDS to extend what is already here. That makes this seed the difference
 * between a working demo and a broken one during a TCMB outage, and it is what keeps the
 * integration tests hermetic.
 *
 * <p>A Java migration rather than a generated {@code .sql} file because §6.3 asks for a CSV to be
 * checked in: this way the CSV in {@code db/seed} is the artefact, readable and diffable, instead
 * of being transcribed into three hundred INSERT statements that then have to be kept in step with
 * it.
 */
public class V5__Seed_cpi_history extends BaseJavaMigration {

  private static final String CSV = "/db/seed/cpi-tr-2003-base.csv";

  /**
   * TCMB EVDS series carrying the Turkish CPI general index, 2003=100.
   *
   * <p>SPEC §6.2 names {@code TP.FG.J0}. Checked against the live service on 2026-09-04, that code
   * returns the same index but stops at 2026-01, while {@code TP.GENENDEKS.T1} carries the
   * identical figures — all 277 overlapping months agree to the kuruş — and continues to the latest
   * release. §6.1 says to verify empirically rather than trust any document including the spec, so
   * this is the live one.
   */
  public static final String SERIES_CODE = "TP.GENENDEKS.T1";

  @Override
  public void migrate(Context context) throws Exception {
    String sql =
        """
        insert into cpi_observations (series_code, month, index_value, source, fetched_at)
        values (?, ?, ?, 'SEED', now())
        on conflict (series_code, month) do nothing
        """;
    try (InputStream in = open();
        BufferedReader reader =
            new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
        PreparedStatement statement = context.getConnection().prepareStatement(sql)) {

      String line;
      while ((line = reader.readLine()) != null) {
        String row = line.trim();
        if (row.isEmpty() || row.startsWith("#") || row.startsWith("month,")) {
          continue;
        }
        String[] cells = row.split(",", 2);
        if (cells.length != 2) {
          throw new IllegalStateException("malformed CPI seed row: " + row);
        }
        YearMonth month = YearMonth.parse(cells[0].trim());
        statement.setString(1, SERIES_CODE);
        statement.setObject(2, LocalDate.of(month.getYear(), month.getMonth(), 1));
        statement.setBigDecimal(3, new BigDecimal(cells[1].trim()));
        statement.addBatch();
      }
      statement.executeBatch();
    }
  }

  private static InputStream open() throws IOException {
    InputStream in = V5__Seed_cpi_history.class.getResourceAsStream(CSV);
    if (in == null) {
      throw new IOException("missing CPI seed " + CSV + " on the classpath");
    }
    return in;
  }
}
