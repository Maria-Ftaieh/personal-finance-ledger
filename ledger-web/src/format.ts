import type { Money } from "./api";
import { INTL_LOCALE } from "./i18n";

/*
 * SPEC §8.2: currency through `Intl.NumberFormat`, never a hand-rolled thousands separator.
 */

const currencyFormatters = new Map<string, Intl.NumberFormat>();

function currencyFormatter(currency: string): Intl.NumberFormat {
  let formatter = currencyFormatters.get(currency);
  if (!formatter) {
    formatter = new Intl.NumberFormat(INTL_LOCALE, { style: "currency", currency });
    currencyFormatters.set(currency, formatter);
  }
  return formatter;
}

/**
 * Formats an amount for display.
 *
 * The decimal **string** is handed to `Intl` rather than a parsed number: Intl.NumberFormat
 * accepts a string and formats it exactly, so the value never passes through a double on
 * its way to the screen. Engines without that (pre-2022) fall back to `Number`, which is
 * accurate enough to read but is the thing the server-side `Money` type exists to avoid.
 */
export function formatMoney(money: Money): string {
  const formatter = currencyFormatter(money.currency);
  try {
    return formatter.format(money.amount as unknown as number);
  } catch {
    return formatter.format(Number(money.amount));
  }
}

/** Signed, for a change figure where the direction is the point. */
export function formatSignedMoney(money: Money): string {
  const formatted = formatMoney(money);
  return isNegative(money) ? formatted : `+${formatted}`;
}

export function isNegative(money: Money): boolean {
  return money.amount.trimStart().startsWith("-");
}

export function isZero(money: Money): boolean {
  return Number(money.amount) === 0;
}

/**
 * For chart geometry and share-of-total bars only.
 *
 * Never for a figure the user reads as money, and never to add two amounts together — the
 * server does all of that in exact decimal and sends the answer down.
 */
export function toNumber(money: Money): number {
  return Number(money.amount);
}

const monthFormatter = new Intl.DateTimeFormat(INTL_LOCALE, { month: "long", year: "numeric" });
const shortMonthFormatter = new Intl.DateTimeFormat(INTL_LOCALE, { month: "short", year: "2-digit" });
const dateFormatter = new Intl.DateTimeFormat(INTL_LOCALE, {
  day: "2-digit",
  month: "2-digit",
  year: "numeric",
});

/** `2026-01` → "Ocak 2026". */
export function formatMonth(yearMonth: string): string {
  return monthFormatter.format(monthToDate(yearMonth));
}

/** `2026-01` → "Oca 26", for an axis where space is short. */
export function formatMonthShort(yearMonth: string): string {
  return shortMonthFormatter.format(monthToDate(yearMonth));
}

/** `2026-01-20` → "20.01.2026". */
export function formatDate(isoDate: string): string {
  const [year, month, day] = isoDate.split("-").map(Number);
  return dateFormatter.format(new Date(year ?? 1970, (month ?? 1) - 1, day ?? 1));
}

export function formatPercent(value: number): string {
  return new Intl.NumberFormat(INTL_LOCALE, {
    style: "percent",
    maximumFractionDigits: 1,
    signDisplay: "exceptZero",
  }).format(value / 100);
}

function monthToDate(yearMonth: string): Date {
  const [year, month] = yearMonth.split("-").map(Number);
  return new Date(year ?? 1970, (month ?? 1) - 1, 1);
}

/** `2026-01` → `2025-01`, without going through a Date. */
export function previousYear(yearMonth: string): string {
  const [year, month] = yearMonth.split("-");
  return `${Number(year) - 1}-${month}`;
}
