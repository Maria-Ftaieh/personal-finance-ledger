package dev.ledger.imports;

import dev.ledger.core.model.ImportResult;
import dev.ledger.core.model.ParsedStatement;
import dev.ledger.imports.bank.GarantiBbvaImporter;
import dev.ledger.imports.bank.StatementFormatException;
import dev.ledger.imports.bank.StatementImporter;
import dev.ledger.imports.bank.YapiKrediImporter;
import dev.ledger.imports.csv.GenericCsvImporter;
import dev.ledger.imports.pdf.PdfDocument;
import dev.ledger.imports.pdf.PositionalTextExtractor;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException;

/**
 * The entry point for importing a file: sniffs the format, decrypts if it can, picks an adapter,
 * and returns one of the four {@link ImportResult} cases.
 *
 * <p>Framework free by design (SPEC §1) — Phase 2 wraps this in a controller, it does not replace
 * it.
 */
public final class StatementImportService {

  /**
   * Below this many characters of extracted text, the file is a scan rather than a statement. OCR
   * is out of scope (SPEC §4.3): a clear "unreadable" beats plausible-looking invented rows.
   */
  static final int MIN_TEXT_CHARS = 200;

  private final List<StatementImporter> importers;
  private final GenericCsvImporter csvImporter = new GenericCsvImporter();

  public StatementImportService() {
    // Order matters: the first adapter whose sniff matches wins.
    this(List.of(new GarantiBbvaImporter(), new YapiKrediImporter()));
  }

  public StatementImportService(List<StatementImporter> importers) {
    this.importers = List.copyOf(importers);
  }

  /**
   * @param password the PDF password, or {@code null}. Never logged, never stored, never returned
   *     in the result (SPEC §4.3).
   */
  public ImportResult importFile(byte[] content, String fileName, String password) {
    String hash = sha256(content);
    return isPdf(content)
        ? importPdf(content, fileName, password, hash)
        : importCsv(content, fileName, hash);
  }

  private ImportResult importPdf(byte[] content, String fileName, String password, String hash) {
    try (PDDocument document = Loader.loadPDF(content, password == null ? "" : password)) {
      PdfDocument extracted = PositionalTextExtractor.extract(document, fileName);

      if (extracted.plainText().length() < MIN_TEXT_CHARS) {
        return new ImportResult.Unreadable(
            fileName, "no text layer; scanned statements are not supported");
      }
      for (StatementImporter importer : importers) {
        if (importer.supports(extracted)) {
          return new ImportResult.Parsed(withHash(importer.parse(extracted), hash));
        }
      }
      return new ImportResult.UnsupportedBank(fileName, "no adapter recognised the issuer");
    } catch (InvalidPasswordException e) {
      return new ImportResult.NeedsPassword(fileName);
    } catch (StatementFormatException e) {
      return new ImportResult.Unreadable(fileName, e.getMessage());
    } catch (IOException e) {
      return new ImportResult.Unreadable(fileName, "could not be read as a PDF: " + e.getMessage());
    }
  }

  private ImportResult importCsv(byte[] content, String fileName, String hash) {
    try {
      String csv = new String(content, StandardCharsets.UTF_8);
      return new ImportResult.Parsed(withHash(csvImporter.parse(csv, fileName), hash));
    } catch (RuntimeException e) {
      return new ImportResult.Unreadable(fileName, "could not be read as CSV: " + e.getMessage());
    }
  }

  private static boolean isPdf(byte[] content) {
    return content.length >= 4
        && content[0] == '%'
        && content[1] == 'P'
        && content[2] == 'D'
        && content[3] == 'F';
  }

  private static ParsedStatement withHash(ParsedStatement statement, String hash) {
    return new ParsedStatement(
        statement.id(),
        statement.bank(),
        statement.sourceFileName(),
        hash,
        statement.period(),
        statement.transactions());
  }

  /** SPEC §5.2: the file hash makes a re-upload of the identical file a no-op before parsing. */
  public static String sha256(byte[] content) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 is required by every JVM", e);
    }
  }
}
