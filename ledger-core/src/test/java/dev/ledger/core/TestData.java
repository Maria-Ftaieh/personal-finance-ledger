package dev.ledger.core;

import dev.ledger.core.model.BankCode;
import dev.ledger.core.model.DateRange;
import dev.ledger.core.model.Installment;
import dev.ledger.core.model.ParsedStatement;
import dev.ledger.core.model.StatementId;
import dev.ledger.core.model.Transaction;
import dev.ledger.core.model.TransactionId;
import dev.ledger.core.money.Money;
import dev.ledger.core.parse.TurkishDates;
import java.util.ArrayList;
import java.util.List;

/** Concise construction of statements for tests. */
public final class TestData {

  private TestData() {}

  public static StatementBuilder statement(String periodStart, String periodEnd) {
    return new StatementBuilder(BankCode.GARANTI_BBVA, periodStart, periodEnd);
  }

  public static StatementBuilder statement(BankCode bank, String periodStart, String periodEnd) {
    return new StatementBuilder(bank, periodStart, periodEnd);
  }

  public static final class StatementBuilder {
    private final StatementId id = StatementId.newId();
    private final BankCode bank;
    private final DateRange period;
    private final List<Transaction> transactions = new ArrayList<>();

    private StatementBuilder(BankCode bank, String periodStart, String periodEnd) {
      this.bank = bank;
      this.period = new DateRange(TurkishDates.parse(periodStart), TurkishDates.parse(periodEnd));
    }

    public StatementBuilder line(String date, String description, String amount) {
      return line(date, description, amount, null, null);
    }

    public StatementBuilder installment(
        String date, String description, String amount, int current, int total) {
      return line(date, description, amount, null, new Installment(current, total));
    }

    public StatementBuilder foreign(
        String date, String description, String tryAmount, String originalAmount) {
      return line(date, description, tryAmount, Money.of(originalAmount, Money.EUR), null);
    }

    public StatementBuilder line(
        String date,
        String description,
        String amount,
        Money originalAmount,
        Installment installment) {
      transactions.add(
          Transaction.create(
              TransactionId.newId(),
              TurkishDates.parse(date),
              TurkishDates.parse(date),
              description,
              Money.tryLira(amount),
              originalAmount,
              installment,
              bank,
              id));
      return this;
    }

    public ParsedStatement build() {
      return new ParsedStatement(id, bank, "synthetic.pdf", null, period, transactions);
    }
  }
}
