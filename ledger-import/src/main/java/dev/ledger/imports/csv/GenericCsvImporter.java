package dev.ledger.imports.csv;

import dev.ledger.core.model.BankCode;
import dev.ledger.core.model.DateRange;
import dev.ledger.core.model.Installment;
import dev.ledger.core.model.ParsedStatement;
import dev.ledger.core.model.StatementId;
import dev.ledger.core.model.Transaction;
import dev.ledger.core.model.TransactionId;
import dev.ledger.core.money.Money;
import dev.ledger.core.parse.Installments;
import dev.ledger.core.parse.TurkishDates;
import dev.ledger.core.parse.TurkishNumbers;
import dev.ledger.core.text.TurkishText;
import java.util.ArrayList;
import java.util.Currency;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The fallback path for banks that only export CSV or XLS, and the escape hatch for a bank with no
 * adapter. SPEC §4.2 requires it: without it, an unsupported bank is a dead end for the user.
 *
 * <p>Expected header, in any order, case and accent insensitive. Only the first three are required:
 *
 * <pre>
 * date, description, amount, posting_date, currency, original_amount, original_currency, installment
 * </pre>
 *
 * <p>Amounts are read with {@link TurkishNumbers}, because this path exists for exports from
 * Turkish banks and those write {@code 1.234,56}. A file using {@code 1234.56} still reads
 * correctly; one using {@code 1,234.56} does not, and fails loudly rather than being wrong by three
 * orders of magnitude.
 */
public final class GenericCsvImporter {

  private static final List<String> REQUIRED = List.of("date", "description", "amount");

  public ParsedStatement parse(String csv, String sourceName) {
    List<List<String>> rows = CsvReader.read(csv);
    if (rows.isEmpty()) {
      throw new IllegalArgumentException("CSV file is empty");
    }

    Map<String, Integer> columns = new HashMap<>();
    List<String> header = rows.get(0);
    for (int i = 0; i < header.size(); i++) {
      columns.put(key(header.get(i)), i);
    }
    for (String required : REQUIRED) {
      if (!columns.containsKey(required)) {
        throw new IllegalArgumentException("CSV is missing the '" + required + "' column");
      }
    }

    StatementId statementId = StatementId.newId();
    List<Transaction> transactions = new ArrayList<>();
    for (List<String> row : rows.subList(1, rows.size())) {
      if (row.stream().allMatch(String::isBlank)) {
        continue;
      }
      String date = cell(row, columns, "date");
      String description = cell(row, columns, "description");
      String posting = cell(row, columns, "posting_date");
      String currency = cell(row, columns, "currency");
      String originalAmount = cell(row, columns, "original_amount");
      String originalCurrency = cell(row, columns, "original_currency");
      String installment = cell(row, columns, "installment");

      transactions.add(
          Transaction.create(
              TransactionId.newId(),
              TurkishDates.parse(date),
              posting.isBlank() ? TurkishDates.parse(date) : TurkishDates.parse(posting),
              description,
              Money.of(
                  TurkishNumbers.parseAmount(cell(row, columns, "amount")),
                  currency.isBlank() ? Money.TRY : Currency.getInstance(currency.trim())),
              originalAmount.isBlank() || originalCurrency.isBlank()
                  ? null
                  : Money.of(
                      TurkishNumbers.parseAmount(originalAmount),
                      Currency.getInstance(originalCurrency.trim())),
              installmentOf(installment, description),
              BankCode.GENERIC_CSV,
              statementId));
    }

    if (transactions.isEmpty()) {
      throw new IllegalArgumentException("CSV contains no transaction rows");
    }
    return new ParsedStatement(
        statementId,
        BankCode.GENERIC_CSV,
        sourceName,
        null,
        DateRange.spanning(transactions.stream().map(Transaction::transactionDate).toList()),
        transactions);
  }

  private static Installment installmentOf(String column, String description) {
    return Installments.find(column.isBlank() ? description : column).orElse(null);
  }

  private static String cell(List<String> row, Map<String, Integer> columns, String name) {
    Integer index = columns.get(name);
    return index == null || index >= row.size() ? "" : row.get(index).trim();
  }

  private static String key(String header) {
    return TurkishText.lowerForMatching(TurkishText.foldToAscii(header.trim()))
        .replaceAll("[^a-z0-9]+", "_");
  }
}
