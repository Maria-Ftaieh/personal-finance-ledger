import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { RotateCcw } from "lucide-react";
import { useState } from "react";
import type { Category, Transaction } from "../api";
import { api } from "../api";
import { READ_ONLY } from "../demo";
import { bankLabel, formatDate, formatMoney } from "../format";
import { categoryLabel, t } from "../i18n";
import { Empty, ErrorState, Loading, MoneyText, Panel, Pill } from "../ui";

function lastDayOf(month: string): string {
  const [year, m] = month.split("-").map(Number);
  return new Date(year ?? 1970, m ?? 1, 0).toISOString().slice(0, 10);
}

/** SPEC §8.1: the full table with filters, and the manual category assignment from §5.3. */
export function TransactionsTable({ month, categories }: { month: string; categories: Category[] }) {
  const [from, setFrom] = useState(`${month}-01`);
  const [to, setTo] = useState(lastDayOf(month));
  const [categoryId, setCategoryId] = useState("");
  const [includeConfirmedDuplicates, setIncludeConfirmed] = useState(false);
  const [appliedMonth, setAppliedMonth] = useState(month);

  // Changing the period selector resets the range without fighting the user's own edits.
  if (appliedMonth !== month) {
    setAppliedMonth(month);
    setFrom(`${month}-01`);
    setTo(lastDayOf(month));
  }

  const filters = { from, to, categoryId: categoryId || undefined, includeConfirmedDuplicates };
  const transactions = useQuery({
    queryKey: ["transactions", filters],
    queryFn: () => api.transactions(filters),
  });

  const client = useQueryClient();
  const invalidate = () => {
    void client.invalidateQueries({ queryKey: ["transactions"] });
    void client.invalidateQueries({ queryKey: ["monthly"] });
    void client.invalidateQueries({ queryKey: ["alerts"] });
  };

  const assign = useMutation({
    mutationFn: ({ id, category }: { id: string; category: string }) =>
      api.assignCategory(id, category),
    onSuccess: invalidate,
  });

  const clear = useMutation({ mutationFn: api.clearCategory, onSuccess: invalidate });

  const rows = transactions.data ?? [];

  return (
    <Panel
      title={t("detailed.transactions")}
      note={t("transactions.count", { count: rows.length })}
      flush
    >
      <div className="row" style={{ padding: "0 var(--space-5) var(--space-4)", gap: "var(--space-4)" }}>
        <label className="field">
          <span>{t("transactions.filterFrom")}</span>
          <input type="date" value={from} onChange={(event) => setFrom(event.target.value)} />
        </label>
        <label className="field">
          <span>{t("transactions.filterTo")}</span>
          <input type="date" value={to} onChange={(event) => setTo(event.target.value)} />
        </label>
        <label className="field">
          <span>{t("common.category")}</span>
          <select value={categoryId} onChange={(event) => setCategoryId(event.target.value)}>
            <option value="">{t("common.all")}</option>
            {categories.map((category) => (
              <option key={category.id} value={category.id}>
                {categoryLabel(category.id, category.displayName)}
              </option>
            ))}
          </select>
        </label>
        <label className="row small muted" style={{ gap: 6, alignSelf: "flex-end", paddingBottom: 8 }}>
          <input
            type="checkbox"
            checked={includeConfirmedDuplicates}
            onChange={(event) => setIncludeConfirmed(event.target.checked)}
            style={{ width: 16, height: 16, padding: 0 }}
          />
          {t("transactions.showDuplicates")}
        </label>
      </div>

      {transactions.isLoading ? (
        <Loading />
      ) : transactions.isError ? (
        <ErrorState error={transactions.error} onRetry={() => void transactions.refetch()} />
      ) : rows.length === 0 ? (
        <Empty>{t("common.none")}</Empty>
      ) : (
        <div className="table__scroll">
          <table className="table">
            <thead>
              <tr>
                <th>{t("common.date")}</th>
                <th>{t("common.description")}</th>
                <th>{t("common.category")}</th>
                <th className="align-right">{t("common.amount")}</th>
                <th />
              </tr>
            </thead>
            <tbody>
              {rows.map((transaction) => (
                <Row
                  key={transaction.id}
                  transaction={transaction}
                  categories={categories}
                  onAssign={(category) => assign.mutate({ id: transaction.id, category })}
                  onClear={() => clear.mutate(transaction.id)}
                />
              ))}
            </tbody>
          </table>
        </div>
      )}
    </Panel>
  );
}

function Row({
  transaction,
  categories,
  onAssign,
  onClear,
}: {
  transaction: Transaction;
  categories: Category[];
  onAssign: (categoryId: string) => void;
  onClear: () => void;
}) {
  return (
    <tr>
      <td className="num tertiary" style={{ whiteSpace: "nowrap" }}>
        {formatDate(transaction.transactionDate)}
      </td>
      <td>
        <div className="stack" style={{ gap: 3 }}>
          {/* The raw description, exactly as the bank printed it (SPEC §3.3). */}
          <span>{transaction.rawDescription}</span>
          <span className="row small" style={{ gap: 6 }}>
            <span className="tertiary">{bankLabel(transaction.bank)}</span>
            {transaction.installment && (
              <Pill>
                {t("transactions.installment")} {transaction.installment}
              </Pill>
            )}
            {transaction.originalAmount && (
              <Pill>{formatMoney(transaction.originalAmount)}</Pill>
            )}
            {transaction.duplicateStatus === "SUSPECTED" && (
              <Pill tone="caution">{t("duplicates.matches")}</Pill>
            )}
            {transaction.duplicateStatus === "CONFIRMED" && (
              <Pill tone="negative">{t("duplicates.confirm")}</Pill>
            )}
          </span>
        </div>
      </td>
      <td>
        <div className="row" style={{ gap: 6, flexWrap: "nowrap" }}>
          <select
            value={transaction.categoryId ?? ""}
            onChange={(event) => onAssign(event.target.value)}
            disabled={READ_ONLY}
            title={READ_ONLY ? t("demo.readOnly") : undefined}
            aria-label={t("common.category")}
          >
            {categories.map((category) => (
              <option key={category.id} value={category.id}>
                {categoryLabel(category.id, category.displayName)}
              </option>
            ))}
          </select>
          {transaction.categoryOverride && (
            <button
              type="button"
              className="button button--quiet button--small"
              onClick={onClear}
              disabled={READ_ONLY}
              title={READ_ONLY ? t("demo.readOnly") : t("transactions.override")}
            >
              <RotateCcw size={13} aria-hidden />
            </button>
          )}
        </div>
      </td>
      <td className="align-right">
        <MoneyText money={transaction.amount} />
      </td>
      <td style={{ width: 1 }} />
    </tr>
  );
}
