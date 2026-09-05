import { AlertTriangle, ChevronRight } from "lucide-react";
import type { ReactNode } from "react";
import type { Money, RealAmount } from "./api";
import { formatMoney, formatMonth, isNegative } from "./format";
import { t } from "./i18n";

/** A calm panel: tone and a soft shadow, no border (SPEC §8.2). */
export function Panel({
  title,
  icon,
  note,
  action,
  flush,
  children,
}: {
  title?: string;
  icon?: ReactNode;
  note?: string;
  action?: ReactNode;
  flush?: boolean;
  children: ReactNode;
}) {
  return (
    <section className={flush ? "panel panel--flush" : "panel"}>
      {title && (
        <header className="panel__head" style={flush ? { padding: "20px 24px 12px" } : undefined}>
          <div className="stack stack--tight" style={{ gap: 2 }}>
            <h2 className="panel__title">
              {icon}
              {title}
            </h2>
            {note && <span className="panel__note">{note}</span>}
          </div>
          {action}
        </header>
      )}
      {children}
    </section>
  );
}

export function Pill({
  children,
  tone = "neutral",
}: {
  children: ReactNode;
  tone?: "neutral" | "accent" | "negative" | "positive" | "caution";
}) {
  return <span className={tone === "neutral" ? "pill" : `pill pill--${tone}`}>{children}</span>;
}

export function Empty({ children }: { children: ReactNode }) {
  return <p className="empty">{children}</p>;
}

export function Loading() {
  return (
    <div className="empty row" style={{ justifyContent: "center" }}>
      <span className="spinner" aria-hidden />
      <span className="visually-hidden">{t("common.loading")}</span>
    </div>
  );
}

export function ErrorState({ error, onRetry }: { error: unknown; onRetry?: () => void }) {
  return (
    <div className="empty stack stack--tight" style={{ alignItems: "center" }}>
      <span className="row" style={{ color: "var(--negative)" }}>
        <AlertTriangle size={16} aria-hidden /> {t("common.error")}
      </span>
      <span className="small tertiary">{error instanceof Error ? error.message : ""}</span>
      {onRetry && (
        <button className="button button--small" onClick={onRetry} type="button">
          {t("common.retry")}
        </button>
      )}
    </div>
  );
}

/**
 * SPEC §8.1: the toggle is one obvious control, not a setting nested somewhere.
 */
export function Segmented<T extends string>({
  value,
  options,
  onChange,
  label,
}: {
  value: T;
  options: { value: T; label: string; icon?: ReactNode }[];
  onChange: (value: T) => void;
  label: string;
}) {
  return (
    <div className="segmented" role="group" aria-label={label}>
      {options.map((option) => (
        <button
          key={option.value}
          type="button"
          className="segmented__option"
          aria-pressed={option.value === value}
          onClick={() => onChange(option.value)}
        >
          <span className="row" style={{ gap: 6, flexWrap: "nowrap" }}>
            {option.icon}
            {option.label}
          </span>
        </button>
      ))}
    </div>
  );
}

/** An amount. Semantic red only when it is a refund — a negative number means something. */
export function MoneyText({ money, bold }: { money: Money; bold?: boolean }) {
  return (
    <span className={isNegative(money) ? "num negative" : "num"} style={bold ? { fontWeight: 600 } : undefined}>
      {formatMoney(money)}
    </span>
  );
}

/**
 * A real-terms figure with its base month attached, and the plain statement when the month
 * has no published index yet.
 *
 * SPEC §6.4 wants the base month surfaced prominently — "₺6,200 in today's money" is
 * meaningless without saying which month is today — and §6.2 wants an unadjusted month
 * labelled rather than quietly presented as real.
 */
export function RealFigure({ amount, size = "large" }: { amount: RealAmount; size?: "large" | "small" }) {
  if (!amount.adjusted) {
    return (
      <div className="stack stack--tight" style={{ gap: 4 }}>
        <span className={size === "large" ? "headline-figure num" : "num"}>
          {formatMoney(amount.nominal)}
        </span>
        <span className="row small" style={{ color: "var(--caution)", gap: 6 }}>
          <AlertTriangle size={13} aria-hidden />
          {t("money.notAdjusted")}
        </span>
      </div>
    );
  }
  return (
    <div className="stack stack--tight" style={{ gap: 4 }}>
      <span className={size === "large" ? "headline-figure num" : "num"}>
        {formatMoney(amount.real)}
      </span>
      <span className="small muted">
        {t("money.baseMonth", { month: formatMonth(amount.baseMonth) })} ·{" "}
        <span className="num">{formatMoney(amount.nominal)}</span> {t("money.nominal").toLowerCase()}
      </span>
    </div>
  );
}

/** A share-of-total bar. Geometry only — the figures beside it come from the server. */
export function Bar({ fraction, over }: { fraction: number; over?: boolean }) {
  const width = Math.max(0, Math.min(1, fraction)) * 100;
  return (
    <div className="bar">
      <div className={over ? "bar__fill bar__fill--over" : "bar__fill"} style={{ width: `${width}%` }} />
    </div>
  );
}

export function Disclosure({
  open,
  onToggle,
  children,
}: {
  open: boolean;
  onToggle: () => void;
  children: ReactNode;
}) {
  return (
    <button type="button" className="disclosure" onClick={onToggle} aria-expanded={open}>
      <ChevronRight
        size={15}
        aria-hidden
        className={open ? "disclosure__chevron disclosure__chevron--open" : "disclosure__chevron"}
      />
      {children}
    </button>
  );
}
