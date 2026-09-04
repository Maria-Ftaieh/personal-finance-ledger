package dev.ledger.imports.bank;

import dev.ledger.core.model.BankCode;
import dev.ledger.core.model.DateRange;
import dev.ledger.core.model.ParsedStatement;
import dev.ledger.core.model.StatementId;
import dev.ledger.core.model.Transaction;
import dev.ledger.core.model.TransactionId;
import dev.ledger.core.money.Money;
import dev.ledger.core.parse.Installments;
import dev.ledger.core.parse.TurkishDates;
import dev.ledger.core.parse.TurkishNumbers;
import dev.ledger.core.text.DescriptionNormalizer;
import dev.ledger.imports.pdf.ColumnLayout;
import dev.ledger.imports.pdf.PdfDocument;
import dev.ledger.imports.pdf.TextLine;
import java.util.ArrayList;
import java.util.Currency;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Garanti BBVA Bonus card statement.
 *
 * <p>Five columns, {@code dd.MM.yyyy} dates, a separate posting-date ("valör") column, and refunds
 * printed with a <em>trailing</em> minus — {@code 249,90-}.
 */
public final class GarantiBbvaImporter implements StatementImporter {

  /** The issuer string printed in the page header. Specific by design (SPEC §4.2). */
  private static final String ISSUER = "GARANTI BBVA";

  /** Points from the top of page one within which the issuer name must appear. */
  private static final float HEADER_ZONE = 220f;

  private static final List<String> COLUMNS =
      List.of("İşlem Tarihi", "Valör", "Açıklama", "Döviz", "Tutar");

  private static final Pattern PERIOD =
      Pattern.compile("(\\d{1,2}[./]\\d{1,2}[./]\\d{4})\\s*-\\s*(\\d{1,2}[./]\\d{1,2}[./]\\d{4})");

  private static final Pattern FOREIGN = Pattern.compile("^([A-Z]{3})\\s+(.+)$");

  @Override
  public boolean supports(PdfDocument document) {
    return document.header(HEADER_ZONE).stream()
        .anyMatch(line -> DescriptionNormalizer.normalise(line.text()).contains(ISSUER));
  }

  @Override
  public BankCode bank() {
    return BankCode.GARANTI_BBVA;
  }

  @Override
  public ParsedStatement parse(PdfDocument document) {
    ColumnLayout layout =
        document.lines().stream()
            .map(line -> ColumnLayout.fromHeader(line, COLUMNS))
            .flatMap(Optional::stream)
            .findFirst()
            .orElseThrow(
                () -> new StatementFormatException("no Garanti BBVA transaction table found"));

    StatementId statementId = StatementId.newId();
    List<Transaction> transactions = new ArrayList<>();
    for (TextLine line : document.lines()) {
      // A repeated header on page two fails this check, which is why rows are recognised by their
      // content rather than by their position after the header.
      String date = layout.cell(line, "İşlem Tarihi");
      if (!TurkishDates.isDate(date)) {
        continue;
      }
      String amount = layout.cell(line, "Tutar");
      if (!TurkishNumbers.isAmount(amount)) {
        continue;
      }
      String posting = layout.cell(line, "Valör");
      String description = layout.cell(line, "Açıklama");

      transactions.add(
          Transaction.create(
              TransactionId.newId(),
              TurkishDates.parse(date),
              TurkishDates.isDate(posting) ? TurkishDates.parse(posting) : TurkishDates.parse(date),
              description,
              Money.of(TurkishNumbers.parseAmount(amount), Money.TRY),
              foreignAmount(layout.cell(line, "Döviz")),
              Installments.find(description).orElse(null),
              bank(),
              statementId));
    }

    return new ParsedStatement(
        statementId,
        bank(),
        document.fileName(),
        null,
        period(document, transactions),
        transactions);
  }

  /** {@code "EUR 9,99"} — the original amount of a foreign currency purchase (SPEC §4.3). */
  private static Money foreignAmount(String cell) {
    Matcher matcher = FOREIGN.matcher(cell.trim());
    if (!matcher.matches() || !TurkishNumbers.isAmount(matcher.group(2))) {
      return null;
    }
    try {
      return Money.of(
          TurkishNumbers.parseAmount(matcher.group(2)), Currency.getInstance(matcher.group(1)));
    } catch (IllegalArgumentException unknownCurrency) {
      return null;
    }
  }

  private static DateRange period(PdfDocument document, List<Transaction> transactions) {
    Optional<DateRange> declared =
        document.lines().stream()
            .filter(line -> DescriptionNormalizer.normalise(line.text()).startsWith("DONEM"))
            .map(line -> PERIOD.matcher(line.text()))
            .filter(Matcher::find)
            .map(m -> new DateRange(TurkishDates.parse(m.group(1)), TurkishDates.parse(m.group(2))))
            .findFirst();
    if (declared.isPresent()) {
      return declared.get();
    }
    if (transactions.isEmpty()) {
      throw new StatementFormatException("statement declares no period and has no transactions");
    }
    return DateRange.spanning(transactions.stream().map(Transaction::transactionDate).toList());
  }
}
