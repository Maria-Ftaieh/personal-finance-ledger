import { useQueries, useQuery } from "@tanstack/react-query";
import { BellRing, CalendarClock, TrendingDown, TrendingUp } from "lucide-react";
import { api } from "../api";
import {
  formatMoney,
  formatMonth,
  formatPercent,
  formatSignedMoney,
  isNegative,
  toNumber,
} from "../format";
import { t } from "../i18n";
import { Bar, Empty, ErrorState, Loading, MoneyText, Panel, Pill, RealFigure } from "../ui";

/**
 * SPEC §8.1: this month's total, the real-terms comparison with the same month last year,
 * the top five categories and any budget alert — answering "how am I doing" in one screen,
 * with nothing to click.
 */
export function BasicView({ month, loading }: { month?: string; loading: boolean }) {
  const results = useQueries({
    queries: [
      {
        queryKey: ["monthly", month],
        queryFn: () => api.monthly(month!),
        enabled: Boolean(month),
      },
      {
        queryKey: ["yoy", month],
        queryFn: () => api.yearOverYear(month!),
        enabled: Boolean(month),
      },
      {
        queryKey: ["alerts", month],
        queryFn: () => api.alerts(month!),
        enabled: Boolean(month),
      },
    ],
  });
  const [monthly, yoy, alerts] = results;
  const categories = useQuery({ queryKey: ["categories"], queryFn: api.categories });

  const nameOf = (id: string) =>
    categories.data?.find((category) => category.id === id)?.displayName ?? id;

  if (loading) {
    return <Loading />;
  }
  if (!month) {
    return (
      <Panel>
        <Empty>{t("basic.noSpending")}</Empty>
      </Panel>
    );
  }
  if (monthly.isError) {
    return <ErrorState error={monthly.error} onRetry={() => void monthly.refetch()} />;
  }

  const report = monthly.data;
  const comparison = yoy.data;
  const total = report ? toNumber(report.total.real) : 0;
  const topFive = report ? report.categories.filter((line) => !isNegative(line.amount.real)).slice(0, 5) : [];

  return (
    <div className="stack">
      <div className="grid grid--halves">
        <Panel title={t("basic.thisMonth")} icon={<CalendarClock size={16} aria-hidden />}>
          {monthly.isLoading || !report ? (
            <Loading />
          ) : (
            <div className="stack stack--tight">
              <span className="caps">{t("basic.spentIn", { month: formatMonth(report.month) })}</span>
              <RealFigure amount={report.total} />
            </div>
          )}
        </Panel>

        <Panel title={t("basic.vsLastYear")} icon={<TrendingUp size={16} aria-hidden />}>
          {yoy.isLoading || !comparison ? (
            <Loading />
          ) : !comparison.comparable ? (
            <div className="stack stack--tight">
              <RealFigure amount={comparison.thisYear} size="small" />
              <span className="small muted">{t("basic.notComparable")}</span>
            </div>
          ) : (
            <div className="stack stack--tight">
              <span className="caps">{formatMonth(comparison.comparedWith)}</span>
              <div className="row" style={{ gap: "var(--space-4)", alignItems: "baseline" }}>
                {/*
                  Colour carries meaning, not sign (SPEC §8.2). Spending less in real terms
                  is the good outcome, so a fall is green and a rise is amber — the opposite
                  of the plain "negative numbers are red" rule that applies to a refund.
                */}
                <span
                  className={
                    isNegative(comparison.realChange)
                      ? "headline-figure num positive"
                      : "headline-figure num caution"
                  }
                >
                  {formatSignedMoney(comparison.realChange)}
                </span>
                {comparison.realChangePercent !== undefined && (
                  <Pill tone={isNegative(comparison.realChange) ? "positive" : "caution"}>
                    {isNegative(comparison.realChange) ? (
                      <TrendingDown size={12} aria-hidden />
                    ) : (
                      <TrendingUp size={12} aria-hidden />
                    )}
                    {formatPercent(comparison.realChangePercent)}
                  </Pill>
                )}
              </div>
              <span className="small muted">
                {t("basic.realChange")} ·{" "}
                <MoneyText money={comparison.lastYear.real} /> → <MoneyText money={comparison.thisYear.real} />
              </span>
            </div>
          )}
        </Panel>
      </div>

      <Panel title={t("basic.topCategories")}>
        {monthly.isLoading ? (
          <Loading />
        ) : topFive.length === 0 ? (
          <Empty>{t("basic.noSpending")}</Empty>
        ) : (
          <div className="stack stack--tight">
            {topFive.map((line) => (
              <div key={line.categoryId} className="stack" style={{ gap: 6 }}>
                <div className="row row--between" style={{ gap: "var(--space-3)" }}>
                  <span>{nameOf(line.categoryId)}</span>
                  <MoneyText money={line.amount.real} />
                </div>
                <Bar fraction={total > 0 ? toNumber(line.amount.real) / total : 0} />
              </div>
            ))}
          </div>
        )}
      </Panel>

      <Panel title={t("basic.alerts")} icon={<BellRing size={16} aria-hidden />}>
        {alerts.isLoading ? (
          <Loading />
        ) : !alerts.data || alerts.data.length === 0 ? (
          <Empty>{t("basic.noAlerts")}</Empty>
        ) : (
          <div className="stack stack--tight">
            {alerts.data.map((alert) => (
              <div key={alert.id} className="stack" style={{ gap: 6 }}>
                <div className="row row--between">
                  <span>{nameOf(alert.categoryId)}</span>
                  <Pill tone="negative">{t("basic.overBy", { amount: formatMoney(alert.overspend) })}</Pill>
                </div>
                <Bar
                  fraction={toNumber(alert.spent) / Math.max(toNumber(alert.budget), 1)}
                  over
                />
                <span className="small tertiary">
                  <MoneyText money={alert.spent} /> / <MoneyText money={alert.budget} />
                </span>
              </div>
            ))}
          </div>
        )}
      </Panel>
    </div>
  );
}
