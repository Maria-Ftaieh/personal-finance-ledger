import { useQuery } from "@tanstack/react-query";
import { useState } from "react";
import type { Category } from "../api";
import { api } from "../api";
import { formatMonth, isNegative, toNumber } from "../format";
import { categoryLabel, t } from "../i18n";
import { Bar, Disclosure, Empty, ErrorState, Loading, MoneyText, Panel, Pill } from "../ui";

/** SPEC §8.1: the breakdown drills down from category to subcategory. */
export function CategoryBreakdown({ month, categories }: { month: string; categories: Category[] }) {
  const [open, setOpen] = useState<string | null>(null);
  const report = useQuery({ queryKey: ["monthly", month], queryFn: () => api.monthly(month) });

  const categoryName = (id: string) =>
    categoryLabel(id, categories.find((category) => category.id === id)?.displayName);

  const subcategoryName = (id: string) => {
    for (const category of categories) {
      const found = category.subcategories.find((sub) => sub.id === id);
      if (found) {
        return categoryLabel(id, found.displayName);
      }
    }
    return categoryLabel(id);
  };

  if (report.isLoading) {
    return (
      <Panel title={t("detailed.breakdown")}>
        <Loading />
      </Panel>
    );
  }
  if (report.isError || !report.data) {
    return (
      <Panel title={t("detailed.breakdown")}>
        <ErrorState error={report.error} onRetry={() => void report.refetch()} />
      </Panel>
    );
  }

  const data = report.data;
  const total = toNumber(data.total.real);

  return (
    <Panel
      title={t("detailed.breakdown")}
      note={
        data.inflationAdjusted
          ? t("money.baseMonth", { month: formatMonth(data.baseMonth) })
          : t("money.notAdjustedWhy")
      }
      action={data.inflationAdjusted ? undefined : <Pill tone="caution">{t("money.notAdjusted")}</Pill>}
    >
      {data.categories.length === 0 ? (
        <Empty>{t("basic.noSpending")}</Empty>
      ) : (
        <div className="stack stack--tight">
          {data.categories.map((line) => {
            const expanded = open === line.categoryId;
            return (
              <div key={line.categoryId} className="stack" style={{ gap: 6 }}>
                <div className="row row--between" style={{ gap: "var(--space-3)" }}>
                  <Disclosure
                    open={expanded}
                    onToggle={() => setOpen(expanded ? null : line.categoryId)}
                  >
                    {categoryName(line.categoryId)}
                  </Disclosure>
                  <span className="row" style={{ gap: "var(--space-3)" }}>
                    <span className="small tertiary num">
                      {total > 0 && !isNegative(line.amount.real)
                        ? `${Math.round((toNumber(line.amount.real) / total) * 100)}%`
                        : ""}
                    </span>
                    <MoneyText money={line.amount.real} bold />
                  </span>
                </div>

                <Bar
                  fraction={total > 0 ? Math.abs(toNumber(line.amount.real)) / total : 0}
                  over={isNegative(line.amount.real)}
                />

                {expanded && (
                  <ul
                    className="stack fade-in"
                    style={{ listStyle: "none", margin: "4px 0 8px", padding: "0 0 0 22px", gap: 4 }}
                  >
                    {line.subcategories.map((sub) => (
                      <li key={sub.subcategoryId} className="row row--between small">
                        <span className="muted">{subcategoryName(sub.subcategoryId)}</span>
                        <MoneyText money={sub.amount.real} />
                      </li>
                    ))}
                  </ul>
                )}
              </div>
            );
          })}

          <hr className="hairline" style={{ margin: "var(--space-3) 0" }} />
          <div className="row row--between">
            <strong>{t("common.amount")}</strong>
            <MoneyText money={data.total.real} bold />
          </div>
        </div>
      )}
    </Panel>
  );
}
