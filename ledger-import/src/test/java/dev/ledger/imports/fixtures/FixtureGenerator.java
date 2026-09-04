package dev.ledger.imports.fixtures;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.encryption.AccessPermission;
import org.apache.pdfbox.pdmodel.encryption.StandardProtectionPolicy;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.apache.pdfbox.pdmodel.graphics.color.PDColor;
import org.apache.pdfbox.pdmodel.graphics.color.PDDeviceGray;

/**
 * Regenerates the synthetic fixture corpus.
 *
 * <p>SPEC §0 and §7.1: no real statement ever enters this repository, so every fixture is generated
 * here and this generator is committed next to its output so the corpus can be rebuilt or extended
 * without anyone needing a real PDF. Every name, card number and merchant below is invented.
 *
 * <p>Regenerate from the repository root with:
 *
 * <pre>{@code
 * mvn install -DskipTests
 * mvn -pl ledger-import org.codehaus.mojo:exec-maven-plugin:3.1.0:java \
 *     -Dexec.mainClass=dev.ledger.imports.fixtures.FixtureGenerator \
 *     -Dexec.classpathScope=test \
 *     -Dexec.args=ledger-import/src/test/resources/fixtures
 * }</pre>
 */
public final class FixtureGenerator {

  /** The password on the encrypted fixture. Not a secret: this file is public and synthetic. */
  public static final String FIXTURE_PASSWORD = "123456";

  private static final PDRectangle PAGE = PDRectangle.A4;
  private static final float TITLE_SIZE = 12f;
  private static final float BODY_SIZE = 8f;
  private static final float ROW_HEIGHT = 14f;

  private FixtureGenerator() {}

  public static void main(String[] args) throws IOException {
    Path outputDirectory =
        Path.of(args.length > 0 ? args[0] : "ledger-import/src/test/resources/fixtures");
    Files.createDirectories(outputDirectory);

    write(outputDirectory.resolve("garanti-2026-01.pdf"), garantiJanuary(), null);
    write(outputDirectory.resolve("garanti-2026-02.pdf"), garantiFebruary(), null);
    write(
        outputDirectory.resolve("garanti-2026-01-protected.pdf"),
        garantiJanuary(),
        FIXTURE_PASSWORD);
    write(outputDirectory.resolve("yapikredi-2026-01.pdf"), yapiKrediJanuary(), null);
    writeScan(outputDirectory.resolve("scanned-statement.pdf"));
    Files.writeString(
        outputDirectory.resolve("generic-export.csv"), genericCsv(), StandardCharsets.UTF_8);

    System.out.println("fixtures written to " + outputDirectory.toAbsolutePath());
  }

  // ---------------------------------------------------------------- layouts

  /** A column: where its header and its data start, and whether the data is right aligned. */
  private record Column(String header, float left, float right, boolean rightAligned) {}

  private record Statement(List<String> preamble, List<Column> columns, List<List<String>> rows) {}

  private static final List<Column> GARANTI_COLUMNS =
      List.of(
          new Column("İşlem Tarihi", 40, 112, false),
          new Column("Valör", 112, 184, false),
          new Column("Açıklama", 184, 400, false),
          new Column("Döviz", 400, 470, false),
          new Column("Tutar (TL)", 470, 555, true));

  private static final List<Column> YAPI_KREDI_COLUMNS =
      List.of(
          new Column("Tarih", 40, 130, false),
          new Column("Açıklama", 130, 430, false),
          new Column("Tutar (TL)", 430, 555, true));

  private static Statement garantiJanuary() {
    return new Statement(
        List.of(
            "GARANTİ BBVA",
            "Bonus Kart Hesap Özeti",
            "Kart No: **** **** **** 1234",
            "Hesap Kesim Tarihi: 15.01.2026",
            "Dönem: 16.12.2025 - 15.01.2026"),
        GARANTI_COLUMNS,
        List.of(
            row("18.12.2025", "19.12.2025", "A101 KADIKÖY 998877665", "", "154,25"),
            row("27.12.2025", "28.12.2025", "SHELL AKARYAKIT ŞİŞLİ", "", "1.480,00"),
            row("05.01.2026", "06.01.2026", "KAHVE DÜNYASI KADIKÖY", "", "85,00"),
            row("05.01.2026", "06.01.2026", "KAHVE DÜNYASI KADIKÖY", "", "85,00"),
            row("05.01.2026", "06.01.2026", "SPOTIFY AB STOCKHOLM", "EUR 9,99", "354,60"),
            row("07.01.2026", "08.01.2026", "MİGROS TİCARET AŞ İSTANBUL", "", "342,75"),
            row("09.01.2026", "10.01.2026", "APPLE STORE ISTANBUL 3/8", "", "1.250,00"),
            row("12.01.2026", "13.01.2026", "TRENDYOL SİPARİŞ", "", "249,90"),
            row("12.01.2026", "13.01.2026", "TRENDYOL SİPARİŞ İADE", "", "249,90-"),
            row("14.01.2026", "15.01.2026", "KART ÜCRETİ", "", "750,00")));
  }

  /**
   * An interim statement whose period overlaps the January one from 1 to 15 January. The shared
   * days are printed with different reference numbers, a differently truncated merchant name and a
   * differently written instalment marker — which is exactly how the same purchase differs between
   * two real printings, and what the dedup pass has to see through.
   */
  private static Statement garantiFebruary() {
    return new Statement(
        List.of(
            "GARANTİ BBVA",
            "Bonus Kart Hesap Özeti",
            "Kart No: **** **** **** 1234",
            "Hesap Kesim Tarihi: 31.01.2026",
            "Dönem: 01.01.2026 - 31.01.2026"),
        GARANTI_COLUMNS,
        List.of(
            row("05.01.2026", "06.01.2026", "KAHVE DÜNYASI KADIKÖY 112233445", "", "85,00"),
            row("05.01.2026", "06.01.2026", "KAHVE DÜNYASI KADIKÖY 112233446", "", "85,00"),
            row("05.01.2026", "06.01.2026", "SPOTIFY AB STOCKHOLM", "EUR 9,99", "354,60"),
            row("07.01.2026", "08.01.2026", "MİGROS TİCARET A", "", "342,75"),
            row("09.01.2026", "10.01.2026", "APPLE STORE ISTANBUL 03/08", "", "1.250,00"),
            row("12.01.2026", "13.01.2026", "TRENDYOL SİPARİŞ", "", "249,90"),
            row("12.01.2026", "13.01.2026", "TRENDYOL SİPARİŞ İADE", "", "249,90-"),
            row("20.01.2026", "21.01.2026", "GETİR MARKET KADIKÖY", "", "128,40"),
            row("25.01.2026", "26.01.2026", "THY İSTANBUL İZMİR", "", "2.480,00")));
  }

  private static Statement yapiKrediJanuary() {
    return new Statement(
        List.of(
            "Yapı Kredi Bankası A.Ş.",
            "World Kart Hesap Özeti",
            "Kart No: **** **** **** 5678",
            "Dönem : 01 Oca 2026 - 31 Oca 2026"),
        YAPI_KREDI_COLUMNS,
        List.of(
            row("03 Oca 2026", "BİM BİRLEŞİK MAĞAZALAR", "287,60"),
            row("09 Oca 2026", "APPLE STORE ISTANBUL 3/8", "1.250,00"),
            row("15 Oca 2026", "ECZANE ŞİFA KADIKÖY", "156,00"),
            row("21 Oca 2026", "NETFLIX COM 998877665", "229,99"),
            row("28 Oca 2026", "NETFLIX COM İADE", "-229,99")));
  }

  private static String genericCsv() {
    return """
        date,posting_date,description,amount,currency,original_amount,original_currency,installment
        05.01.2026,06.01.2026,KAHVE DÜNYASI KADIKÖY,"85,00",TRY,,,
        09.01.2026,10.01.2026,APPLE STORE ISTANBUL,"1.250,00",TRY,,,3/8
        05.01.2026,06.01.2026,SPOTIFY AB STOCKHOLM,"354,60",TRY,"9,99",EUR,
        12.01.2026,13.01.2026,"TRENDYOL SİPARİŞ, İADE","-249,90",TRY,,,
        """;
  }

  private static List<String> row(String... cells) {
    return List.of(cells);
  }

  // ---------------------------------------------------------------- writing

  private static void write(Path target, Statement statement, String password) throws IOException {
    try (PDDocument document = new PDDocument()) {
      PDPage page = new PDPage(PAGE);
      document.addPage(page);
      PDType0Font font = loadFont(document);

      try (PDPageContentStream content = new PDPageContentStream(document, page)) {
        float y = PAGE.getHeight() - 52;
        for (int i = 0; i < statement.preamble().size(); i++) {
          text(content, font, i == 0 ? TITLE_SIZE : BODY_SIZE, 40, y, statement.preamble().get(i));
          y -= i == 0 ? 20 : 15;
        }

        y -= 12;
        for (Column column : statement.columns()) {
          text(content, font, BODY_SIZE, column.left(), y, column.header());
        }

        y -= ROW_HEIGHT;
        for (List<String> row : statement.rows()) {
          for (int i = 0; i < row.size(); i++) {
            Column column = statement.columns().get(i);
            String cell = row.get(i);
            if (cell.isEmpty()) {
              continue;
            }
            float x =
                column.rightAligned()
                    ? column.right() - width(font, BODY_SIZE, cell)
                    : column.left();
            text(content, font, BODY_SIZE, x, y, cell);
          }
          y -= ROW_HEIGHT;
        }
      }

      if (password != null) {
        AccessPermission permissions = new AccessPermission();
        StandardProtectionPolicy policy =
            new StandardProtectionPolicy(password + "-owner", password, permissions);
        policy.setEncryptionKeyLength(128);
        document.protect(policy);
      }
      document.save(target.toFile());
    }
  }

  /** A page with ink but no text layer: what a scanned statement looks like to PDFBox. */
  private static void writeScan(Path target) throws IOException {
    try (PDDocument document = new PDDocument()) {
      PDPage page = new PDPage(PAGE);
      document.addPage(page);
      try (PDPageContentStream content = new PDPageContentStream(document, page)) {
        content.setNonStrokingColor(new PDColor(new float[] {0.85f}, PDDeviceGray.INSTANCE));
        content.addRect(40, 500, 515, 280);
        content.fill();
      }
      document.save(target.toFile());
    }
  }

  private static void text(
      PDPageContentStream content, PDType0Font font, float size, float x, float y, String value)
      throws IOException {
    content.beginText();
    content.setFont(font, size);
    content.newLineAtOffset(x, y);
    content.showText(value);
    content.endText();
  }

  private static float width(PDType0Font font, float size, String value) throws IOException {
    return font.getStringWidth(value) / 1000 * size;
  }

  /**
   * DejaVu Sans is committed under {@code src/test/resources/fonts} because the PDF standard-14
   * fonts encode Windows-1252, which has no {@code ı}, {@code ğ}, {@code ş} or {@code İ} — the very
   * characters these fixtures exist to exercise.
   */
  private static PDType0Font loadFont(PDDocument document) throws IOException {
    try (InputStream font = FixtureGenerator.class.getResourceAsStream("/fonts/DejaVuSans.ttf")) {
      if (font == null) {
        throw new IOException("missing /fonts/DejaVuSans.ttf on the test classpath");
      }
      return PDType0Font.load(document, font, true);
    }
  }
}
