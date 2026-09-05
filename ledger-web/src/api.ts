/*
 * The API as the backend actually serves it.
 *
 * Note `Money.amount` is a **string**. That is deliberate on the server side (SPEC §3.1):
 * a JSON number becomes a JavaScript double and 1234.56 stops being 1234.56. Nothing in
 * this application does arithmetic on it — every total, every deflated figure and every
 * difference is computed in `BigDecimal` on the server and sent down already correct.
 * `toNumber` exists only for chart geometry, where a pixel is the unit anyway.
 */

export interface Money {
  amount: string;
  currency: string;
}

export interface RealAmount {
  nominal: Money;
  real: Money;
  baseMonth: string;
  /** False when the month has no published CPI: `real` is then just `nominal` (SPEC §6.2). */
  adjusted: boolean;
}

export type DuplicateStatus = "NONE" | "SUSPECTED" | "CONFIRMED" | "REJECTED";
export type MatchType = "CONTAINS" | "STARTS_WITH" | "EXACT" | "REGEX";

export interface Transaction {
  id: string;
  statementId: string;
  transactionDate: string;
  postingDate: string;
  rawDescription: string;
  normalisedDescription: string;
  amount: Money;
  originalAmount?: Money;
  installment?: string;
  bank: string;
  categoryId?: string;
  subcategoryId?: string;
  categoryOverride: boolean;
  duplicateStatus: DuplicateStatus;
  duplicateOfId?: string;
  duplicateSimilarity?: number;
  duplicateReason?: "DESCRIPTION_SIMILARITY" | "TRUNCATED_DESCRIPTION";
}

export interface Category {
  id: string;
  displayName: string;
  system: boolean;
  subcategories: { id: string; displayName: string }[];
}

export interface Rule {
  id: string;
  priority: number;
  matchType: MatchType;
  pattern: string;
  categoryId: string;
  subcategoryId?: string;
  userDefined: boolean;
}

export interface RulePreview {
  examined: number;
  wouldChange: number;
  heldByOverride: number;
}

export interface MonthlyReport {
  month: string;
  baseMonth: string;
  inflationAdjusted: boolean;
  total: RealAmount;
  categories: {
    categoryId: string;
    amount: RealAmount;
    subcategories: { subcategoryId: string; amount: RealAmount }[];
  }[];
}

export interface YearOverYearReport {
  month: string;
  comparedWith: string;
  baseMonth: string;
  comparable: boolean;
  thisYear: RealAmount;
  lastYear: RealAmount;
  realChange: Money;
  realChangePercent?: number;
  categories: {
    categoryId: string;
    thisYear: RealAmount;
    lastYear: RealAmount;
    realChange: Money;
  }[];
}

export interface Budget {
  id: string;
  categoryId: string;
  limit: Money;
}

export interface BudgetAlert {
  id: string;
  categoryId: string;
  month: string;
  budget: Money;
  spent: Money;
  overspend: Money;
  raisedAt: string;
}

export interface CpiStatus {
  months: number;
  earliestMonth?: string;
  latestPublishedMonth?: string;
  levels: Record<string, number>;
}

export interface CpiRefresh {
  seriesCode: string;
  added: number;
  revised: number;
  latestPublishedMonth?: string;
  reached: boolean;
  detail?: string;
}

export type ImportStatus =
  | "IMPORTED"
  | "ALREADY_IMPORTED"
  /** A read-only demo read the file and deliberately kept none of it. */
  | "PARSED_NOT_STORED"
  | "NEEDS_PASSWORD"
  | "UNSUPPORTED_BANK"
  | "UNREADABLE";

export interface ImportOutcome {
  status: ImportStatus;
  statementId?: string;
  transactionsImported: number;
  suspectedDuplicates: number;
  detail?: string;
}

/** The shape `ApiExceptionHandler` returns for a 4xx. */
export interface ApiError {
  status: number;
  error: string;
  message: string;
}

export class ApiFailure extends Error {
  readonly status: number;

  constructor(status: number, message: string) {
    super(message);
    this.status = status;
    this.name = "ApiFailure";
  }
}

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const response = await fetch(`/api${path}`, {
    headers: init?.body instanceof FormData ? undefined : { "Content-Type": "application/json" },
    ...init,
  });

  if (response.status === 204) {
    return undefined as T;
  }

  const text = await response.text();
  const body: unknown = text ? JSON.parse(text) : undefined;

  if (!response.ok) {
    const message =
      body && typeof body === "object" && "message" in body
        ? String((body as ApiError).message)
        : response.statusText;
    throw new ApiFailure(response.status, message);
  }
  return body as T;
}

const query = (params: Record<string, string | number | boolean | undefined>) => {
  const search = new URLSearchParams();
  for (const [key, value] of Object.entries(params)) {
    if (value !== undefined && value !== "") {
      search.set(key, String(value));
    }
  }
  const rendered = search.toString();
  return rendered ? `?${rendered}` : "";
};

export const api = {
  categories: () => request<Category[]>("/categories"),

  monthsWithSpending: () => request<string[]>("/reports/months"),

  monthly: (month: string, baseMonth?: string) =>
    request<MonthlyReport>(`/reports/monthly${query({ month, baseMonth })}`),

  yearOverYear: (month: string, baseMonth?: string) =>
    request<YearOverYearReport>(`/reports/year-over-year${query({ month, baseMonth })}`),

  transactions: (filters: {
    from?: string;
    to?: string;
    categoryId?: string;
    includeConfirmedDuplicates?: boolean;
  }) => request<Transaction[]>(`/transactions${query(filters)}`),

  assignCategory: (id: string, categoryId: string, subcategoryId?: string) =>
    request<Transaction>(`/transactions/${id}/category`, {
      method: "PUT",
      body: JSON.stringify({ categoryId, subcategoryId }),
    }),

  clearCategory: (id: string) =>
    request<Transaction>(`/transactions/${id}/category`, { method: "DELETE" }),

  duplicates: () => request<Transaction[]>("/duplicates"),

  confirmDuplicate: (id: string) =>
    request<Transaction>(`/duplicates/${id}/confirm`, { method: "POST" }),

  rejectDuplicate: (id: string) =>
    request<Transaction>(`/duplicates/${id}/reject`, { method: "POST" }),

  rules: () => request<Rule[]>("/rules"),

  createRule: (rule: Omit<Rule, "id" | "userDefined">) =>
    request<Rule>("/rules", { method: "POST", body: JSON.stringify(rule) }),

  deleteRule: (id: string) => request<void>(`/rules/${id}`, { method: "DELETE" }),

  rulePreview: () => request<RulePreview>("/rules/preview"),

  reevaluateRules: () => request<{ changed: number }>("/rules/reevaluate", { method: "POST" }),

  budgets: () => request<Budget[]>("/budgets"),

  setBudget: (categoryId: string, amount: string, currency = "TRY") =>
    request<Budget>(`/budgets/${categoryId}`, {
      method: "PUT",
      body: JSON.stringify({ amount, currency }),
    }),

  deleteBudget: (categoryId: string) =>
    request<void>(`/budgets/${categoryId}`, { method: "DELETE" }),

  alerts: (month?: string) => request<BudgetAlert[]>(`/alerts${query({ month })}`),

  cpi: () => request<CpiStatus>("/cpi"),

  refreshCpi: () => request<CpiRefresh>("/cpi/refresh", { method: "POST" }),

  /** The upload endpoint answers 422 with a usable body for the three failure outcomes. */
  upload: async (file: File, password?: string): Promise<ImportOutcome> => {
    const form = new FormData();
    form.append("file", file);
    if (password) {
      form.append("password", password);
    }
    const response = await fetch("/api/statements", { method: "POST", body: form });
    const outcome = (await response.json()) as ImportOutcome;
    if (!response.ok && response.status !== 422) {
      throw new ApiFailure(response.status, response.statusText);
    }
    return outcome;
  },
};
