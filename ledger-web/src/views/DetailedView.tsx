import { useQuery } from "@tanstack/react-query";
import { useState } from "react";
import { api } from "../api";
import { formatMonth } from "../format";
import { t } from "../i18n";
import { Empty, Loading, Panel } from "../ui";
import { CategoryBreakdown } from "./CategoryBreakdown";
import { DuplicateQueue } from "./DuplicateQueue";
import { RulesPanel } from "./RulesPanel";
import { TrendChart } from "./TrendChart";
import { TransactionsTable } from "./TransactionsTable";
import { UploadPanel } from "./UploadPanel";

/**
 * SPEC §8.1: the transaction table with filters, the category breakdown with drill-down to
 * subcategory, the month-over-month trend, the duplicate review queue and rule management.
 */
export function DetailedView({ months, loading }: { months: string[]; loading: boolean }) {
  const [month, setMonth] = useState<string | undefined>(undefined);
  const categories = useQuery({ queryKey: ["categories"], queryFn: api.categories });

  const selected = month ?? months[0];

  if (loading) {
    return <Loading />;
  }

  return (
    <div className="stack">
      <UploadPanel />

      {!selected ? (
        <Panel>
          <Empty>{t("basic.noSpending")}</Empty>
        </Panel>
      ) : (
        <>
          <Panel>
            <label className="field" style={{ maxWidth: 260 }}>
              <span>{t("detailed.month")}</span>
              <select value={selected} onChange={(event) => setMonth(event.target.value)}>
                {months.map((value) => (
                  <option key={value} value={value}>
                    {formatMonth(value)}
                  </option>
                ))}
              </select>
            </label>
          </Panel>

          <CategoryBreakdown month={selected} categories={categories.data ?? []} />

          <TrendChart months={months} upTo={selected} />

          <TransactionsTable month={selected} categories={categories.data ?? []} />
        </>
      )}

      <DuplicateQueue />

      <RulesPanel categories={categories.data ?? []} />
    </div>
  );
}
