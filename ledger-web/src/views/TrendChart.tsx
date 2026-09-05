import { useQueries } from "@tanstack/react-query";
import {
  Area,
  AreaChart,
  CartesianGrid,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from "recharts";
import { api } from "../api";
import { formatMoney, formatMonth, formatMonthShort, toNumber } from "../format";
import { INTL_LOCALE, t } from "../i18n";
import { Empty, Loading, Panel } from "../ui";

const MONTHS_SHOWN = 12;

/** One plotted month. `real` and `nominal` are numbers for geometry only — see format.ts. */
interface Point {
  month: string;
  label: string;
  real: number;
  nominal: number;
  adjusted: boolean;
  currency: string;
}

/**
 * Month-on-month spending in real terms.
 *
 * Every month is deflated to the same base by the server, so the line shows what actually
 * happened to spending rather than what inflation did to the numbers. Comparing nominal
 * monthly totals in a thirty-per-cent economy would draw a rising line for a household
 * spending steadily less, which is the opposite of useful.
 *
 * The months are fetched one request each rather than through a bulk endpoint: they are
 * small, TanStack Query caches them, and they are the same responses the breakdown panel
 * already asked for.
 */
export function TrendChart({ months, upTo }: { months: string[]; upTo: string }) {
  const visibleMonths = months.filter((month) => month <= upTo).slice(0, MONTHS_SHOWN).reverse();

  const reports = useQueries({
    queries: visibleMonths.map((month) => ({
      queryKey: ["monthly", month],
      queryFn: () => api.monthly(month),
    })),
  });

  if (reports.some((report) => report.isLoading)) {
    return (
      <Panel title={t("detailed.trend")}>
        <Loading />
      </Panel>
    );
  }

  const points: Point[] = reports
    .map((report) => report.data)
    .filter((data) => data !== undefined)
    .map((data) => ({
      month: data.month,
      label: formatMonthShort(data.month),
      real: toNumber(data.total.real),
      nominal: toNumber(data.total.nominal),
      adjusted: data.inflationAdjusted,
      currency: data.total.real.currency,
    }));

  const baseMonth = reports.find((report) => report.data)?.data?.baseMonth;

  if (points.length === 0) {
    return (
      <Panel title={t("detailed.trend")}>
        <Empty>{t("basic.noSpending")}</Empty>
      </Panel>
    );
  }

  const compact = new Intl.NumberFormat(INTL_LOCALE, { notation: "compact", maximumFractionDigits: 1 });

  return (
    <Panel
      title={t("detailed.trend")}
      note={baseMonth ? t("detailed.trendNote", { month: formatMonth(baseMonth) }) : undefined}
    >
      <div style={{ width: "100%", height: 240 }}>
        <ResponsiveContainer>
          <AreaChart data={points} margin={{ top: 8, right: 8, bottom: 0, left: 0 }}>
            <defs>
              {/* A soft wash under one accent line: depth from tone, not from strokes. */}
              <linearGradient id="trend-fill" x1="0" y1="0" x2="0" y2="1">
                <stop offset="0%" stopColor="var(--accent)" stopOpacity={0.22} />
                <stop offset="100%" stopColor="var(--accent)" stopOpacity={0} />
              </linearGradient>
            </defs>
            <CartesianGrid stroke="var(--hairline)" vertical={false} />
            <XAxis
              dataKey="label"
              tickLine={false}
              axisLine={false}
              tick={{ fill: "var(--text-tertiary)", fontSize: 11 }}
              dy={6}
            />
            <YAxis
              tickLine={false}
              axisLine={false}
              width={56}
              tick={{ fill: "var(--text-tertiary)", fontSize: 11 }}
              tickFormatter={(value: number) => compact.format(value)}
            />
            <Tooltip
              cursor={{ stroke: "var(--hairline-strong)" }}
              contentStyle={{
                background: "var(--panel-raised)",
                border: "none",
                borderRadius: "var(--radius-small)",
                boxShadow: "var(--shadow-raised)",
                color: "var(--text)",
                fontSize: 13,
              }}
              labelFormatter={(_label, payload) => {
                const point = payload?.[0]?.payload as Point | undefined;
                return point ? formatMonth(point.month) : "";
              }}
              formatter={(value, _name, entry) => {
                const point = entry.payload as Point;
                const label = point.adjusted ? t("money.real") : t("money.notAdjusted");
                return [formatMoney({ amount: String(value), currency: point.currency }), label];
              }}
            />
            <Area
              type="monotone"
              dataKey="real"
              stroke="var(--accent)"
              strokeWidth={2}
              fill="url(#trend-fill)"
              dot={{ r: 2.5, strokeWidth: 0, fill: "var(--accent)" }}
              activeDot={{ r: 4, strokeWidth: 0 }}
              isAnimationActive={!window.matchMedia?.("(prefers-reduced-motion: reduce)").matches}
              animationDuration={220}
            />
          </AreaChart>
        </ResponsiveContainer>
      </div>
    </Panel>
  );
}
