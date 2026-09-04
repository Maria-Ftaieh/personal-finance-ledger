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
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Yapı Kredi World card statement.
 *
 * <p>Deliberately unlike the Garanti layout, which is the point of having two adapters: three
 * columns instead of five, dates written with an abbreviated Turkish month ({@code 05 Oca 2026})
 * instead of {@code dd.MM.yyyy}, one date column instead of two, and refunds printed with a
 * <em>leading</em> minus rather than a trailing one.
 */
public final class YapiKrediImporter implements StatementImporter {

  private static final String ISSUER = "YAPI KREDI";

  private static final float HEADER_ZONE = 220f;

  private static final List<String> COLUMNS = List.of("Tarih", "Açıklama", "Tutar");

  private static final Pattern PERIOD =
      Pattern.compile("(\\d{1,2}\\s+\\S{3,8}\\s+\\d{4})\\s*-\\s*(\\d{1,2}\\s+\\S{3,8}\\s+\\d{4})");

  @Override
  public boolean supports(PdfDocument document) {
    return document.header(HEADER_ZONE).stream()
        .anyMatch(line -> DescriptionNormalizer.normalise(line.text()).contains(ISSUER));
  }

  @Override
  public BankCode bank() {
    return BankCode.YAPI_KREDI;
  }

  @Override
  public ParsedStatement parse(PdfDocument document) {
    ColumnLayout layout =
        document.lines().stream()
            .map(line -> ColumnLayout.fromHeader(line, COLUMNS))
            .flatMap(Optional::stream)
            .findFirst()
            .orElseThrow(
                () -> new StatementFormatException("no Yapı Kredi transaction table found"));

    StatementId statementId = StatementId.newId();
    List<Transaction> transactions = new ArrayList<>();
    for (TextLine line : document.lines()) {
      String date = layout.cell(line, "Tarih");
      String amount = layout.cell(line, "Tutar");
      if (!TurkishDates.isDate(date) || !TurkishNumbers.isAmount(amount)) {
        continue;
      }
      String description = layout.cell(line, "Açıklama");

      transactions.add(
          Transaction.create(
              TransactionId.newId(),
              TurkishDates.parse(date),
              // No valör column on this layout; the purchase date is the only date printed.
              TurkishDates.parse(date),
              description,
              Money.of(TurkishNumbers.parseAmount(amount), Money.TRY),
              null,
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
