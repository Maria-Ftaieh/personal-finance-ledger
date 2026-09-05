#!/usr/bin/env node
/*
 * Generates a realistic run of synthetic transactions and imports them through the ordinary
 * upload endpoint. SPEC §8.3.
 *
 * Everything here is invented. No real statement, merchant reference or card number is
 * involved, which is why this can live in the repository at all (SPEC §0).
 *
 * Two deliberate choices about the shape of the data:
 *
 *   - it runs for 24 months rather than 12, because the headline feature is the same month
 *     a year apart and a single year gives nothing to compare against;
 *   - nominal amounts drift upwards at roughly the rate Turkish prices actually moved. Real
 *     spending stays close to flat. That is the point the application exists to make: the
 *     nominal line climbs steeply while the real line does not, and a demo whose amounts
 *     were constant would show the opposite of what the user needs to see.
 *
 * Usage:  node scripts/generate-demo-data.mjs [--api http://localhost:8080] [--months 24]
 */

const args = new Map();
for (let i = 2; i < process.argv.length; i += 2) {
  args.set(process.argv[i].replace(/^--/, ""), process.argv[i + 1]);
}

const API = args.get("api") ?? process.env.LEDGER_API ?? "http://localhost:8080";
const MONTHS = Number(args.get("months") ?? 24);
/** Roughly 30% a year, which is the environment these statements come from. */
const MONTHLY_DRIFT = 1.022;

/** Merchants the seeded rules recognise, so the demo lands in real categories. */
const MERCHANTS = [
  { name: "KAHVE DÜNYASI KADIKÖY", base: 85, perMonth: 9, spread: 0.15 },
  { name: "STARBUCKS BAĞDAT CAD", base: 110, perMonth: 4, spread: 0.15 },
  { name: "MİGROS TİCARET AŞ", base: 640, perMonth: 4, spread: 0.35 },
  { name: "A101 KADIKÖY", base: 180, perMonth: 5, spread: 0.4 },
  { name: "BİM BİRLEŞİK MAĞAZALAR", base: 240, perMonth: 3, spread: 0.4 },
  { name: "GETİR MARKET", base: 130, perMonth: 3, spread: 0.5 },
  { name: "YEMEKSEPETİ", base: 190, perMonth: 3, spread: 0.45 },
  { name: "BURGER KING ATAŞEHİR", base: 260, perMonth: 1, spread: 0.3 },
  { name: "SHELL AKARYAKIT ŞİŞLİ", base: 1400, perMonth: 2, spread: 0.25 },
  { name: "OPET KOZYATAĞI", base: 1250, perMonth: 1, spread: 0.25 },
  { name: "İSTANBULKART DOLUM", base: 300, perMonth: 1, spread: 0.1 },
  { name: "BİTAKSİ", base: 210, perMonth: 2, spread: 0.5 },
  { name: "İSPARK OTOPARK", base: 90, perMonth: 2, spread: 0.3 },
  { name: "SPOTIFY AB STOCKHOLM", base: 105, perMonth: 1, spread: 0.02 },
  { name: "NETFLIX COM", base: 230, perMonth: 1, spread: 0.02 },
  { name: "APPLE COM BILL ICLOUD", base: 45, perMonth: 1, spread: 0.02 },
  { name: "STEAM PURCHASE", base: 320, perMonth: 0.4, spread: 0.6 },
  { name: "CINEMAXIMUM KADIKÖY", base: 380, perMonth: 0.6, spread: 0.3 },
  { name: "IDEFIX KİTAP", base: 260, perMonth: 0.5, spread: 0.4 },
  { name: "LC WAIKIKI", base: 720, perMonth: 0.7, spread: 0.5 },
  { name: "DEFACTO MAĞAZA", base: 540, perMonth: 0.5, spread: 0.5 },
  { name: "ECZANE ŞİFA KADIKÖY", base: 210, perMonth: 1.5, spread: 0.5 },
  { name: "MACFIT SPOR SALONU", base: 900, perMonth: 1, spread: 0.05 },
  { name: "TURKCELL FATURA", base: 480, perMonth: 1, spread: 0.08 },
  { name: "ELEKTRIK FATURASI", base: 610, perMonth: 1, spread: 0.3 },
  { name: "DOGALGAZ FATURASI", base: 720, perMonth: 1, spread: 0.6 },
  { name: "IKEA UMRANIYE", base: 1500, perMonth: 0.3, spread: 0.7 },
  { name: "UDEMY COURSE", base: 420, perMonth: 0.3, spread: 0.3 },
  { name: "JETBRAINS SUBSCRIPTION", base: 780, perMonth: 0.15, spread: 0.05 },
  { name: "KART ÜCRETİ", base: 750, perMonth: 0.09, spread: 0 },
];

/** A deterministic generator, so re-running produces the same demo. */
function makeRandom(seed) {
  let state = seed;
  return () => {
    state = (state * 1664525 + 1013904223) % 4294967296;
    return state / 4294967296;
  };
}

const random = makeRandom(20260905);

function turkishAmount(value) {
  return value.toFixed(2).replace(".", ",");
}

function monthsBack(count) {
  const months = [];
  const now = new Date();
  // CPI is published in the first days of the following month, so the current month has
  // none. The demo stops at the previous month, which keeps every figure adjustable.
  for (let i = count; i >= 1; i--) {
    const date = new Date(Date.UTC(now.getUTCFullYear(), now.getUTCMonth() - i, 1));
    months.push({ year: date.getUTCFullYear(), month: date.getUTCMonth() + 1 });
  }
  return months;
}

function buildMonth({ year, month }, drift) {
  const daysInMonth = new Date(Date.UTC(year, month, 0)).getUTCDate();
  const rows = [];

  for (const merchant of MERCHANTS) {
    let occurrences = Math.floor(merchant.perMonth);
    if (random() < merchant.perMonth - occurrences) {
      occurrences += 1;
    }
    for (let i = 0; i < occurrences; i++) {
      const jitter = 1 + (random() - 0.5) * 2 * merchant.spread;
      const amount = merchant.base * drift * jitter;
      const day = 1 + Math.floor(random() * daysInMonth);
      rows.push({
        day,
        description: merchant.name,
        amount: Math.max(amount, 1),
      });
    }
  }

  // One refund a quarter or so, reversing a real purchase, so category totals show a
  // negative reducing them rather than a row being dropped (SPEC §4.3).
  if (random() < 0.35) {
    const source = rows[Math.floor(random() * rows.length)];
    if (source) {
      rows.push({
        day: Math.min(source.day + 3, daysInMonth),
        description: `${source.description} İADE`,
        amount: -source.amount,
      });
    }
  }

  // A running instalment, because Turkish card statements are full of them.
  const instalmentTotal = 8;
  const instalmentIndex = (year * 12 + month) % 14;
  if (instalmentIndex < instalmentTotal) {
    rows.push({
      day: 9,
      description: "APPLE STORE ISTANBUL",
      amount: 1250 * drift,
      installment: `${instalmentIndex + 1}/${instalmentTotal}`,
    });
  }

  rows.sort((a, b) => a.day - b.day);

  const header = "date,posting_date,description,amount,currency,original_amount,original_currency,installment";
  const lines = rows.map((row) => {
    const date = `${String(row.day).padStart(2, "0")}.${String(month).padStart(2, "0")}.${year}`;
    const postingDay = Math.min(row.day + 1, daysInMonth);
    const posting = `${String(postingDay).padStart(2, "0")}.${String(month).padStart(2, "0")}.${year}`;
    return `${date},${posting},"${row.description}","${turkishAmount(row.amount)}",TRY,,,${row.installment ?? ""}`;
  });
  return `${header}\n${lines.join("\n")}\n`;
}

async function upload(name, csv) {
  const form = new FormData();
  form.append("file", new Blob([csv], { type: "text/csv" }), name);
  const response = await fetch(`${API}/api/statements`, { method: "POST", body: form });
  const outcome = await response.json();
  return outcome;
}

async function setBudget(categoryId, amount) {
  await fetch(`${API}/api/budgets/${categoryId}`, {
    method: "PUT",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ amount, currency: "TRY" }),
  });
}

async function main() {
  console.log(`Seeding ${MONTHS} months of synthetic transactions into ${API}`);
  let drift = 1;
  let imported = 0;

  for (const period of monthsBack(MONTHS)) {
    const name = `demo-${period.year}-${String(period.month).padStart(2, "0")}.csv`;
    const outcome = await upload(name, buildMonth(period, drift));
    if (outcome.status === "IMPORTED") {
      imported += outcome.transactionsImported;
      console.log(
        `  ${name}: ${outcome.transactionsImported} transactions, ${outcome.suspectedDuplicates} flagged`,
      );
    } else {
      console.log(`  ${name}: ${outcome.status} ${outcome.detail ?? ""}`);
    }
    drift *= MONTHLY_DRIFT;
  }

  // Budgets set below what the later months actually spend, so the alert panel has content.
  await setBudget("yemek", "6000.00");
  await setBudget("ulasim", "3500.00");
  await setBudget("dijital", "600.00");

  console.log(`Done: ${imported} transactions. Every one of them is fictional.`);
}

main().catch((error) => {
  console.error(error);
  process.exit(1);
});
