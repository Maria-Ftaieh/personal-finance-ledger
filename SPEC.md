# Personal Finance Ledger — Build Specification

A self-hosted personal finance application. It imports Turkish credit card
statements from PDF, deduplicates transactions across overlapping statements,
categorises spending with a user-editable rule engine, and reports both nominal
and inflation-adjusted (real) monthly spending.

This document is the contract for the build. Where it says MUST, treat it as a
hard requirement; where it says SHOULD, deviate only with a note in the PR.

---

## 0. Ground rules

**Never commit real financial data.** No real statement PDFs, no real
transaction exports, no API keys. `.gitignore` MUST cover `*.pdf`, `data/`,
`.env`. All fixtures are synthetic or redacted (see §7.1). Add a pre-commit
hook that rejects staged `.pdf` files outside `src/test/resources/fixtures/`.

**Java version:** 21 LTS or newer. Use `record`, sealed interfaces, pattern
matching for `switch`, and text blocks where they genuinely fit. Do not use
Lombok — the point of this project is to show idiomatic modern Java.

**Build:** Maven or Gradle, pick one and stay with it. Multi-module.

**Language of code and docs:** English. Turkish appears only in data (bank
names, merchant strings, category seed values) and in user-facing UI strings,
which MUST go through an i18n layer rather than being hardcoded.

---

## 1. Module layout

```
ledger-core/        Pure Java. No Spring, no JPA, no HTTP.
ledger-import/      PDF parsing and bank adapters. Depends on core only.
ledger-app/         Spring Boot, PostgreSQL, Flyway, REST API.
ledger-web/         React + TypeScript frontend.
```

`ledger-core` and `ledger-import` MUST NOT have Spring on the classpath. This
boundary is the point — it demonstrates that the domain logic is framework
independent and testable without a container. Enforce it with a dependency
check in CI (Maven Enforcer `bannedDependencies`, or ArchUnit).

---

## 2. Phases

Build in this order. Each phase MUST be green (tests + CI) before the next
starts. Each phase ends with a tagged release and a README section.

| Phase | Deliverable | Done when |
|---|---|---|
| 1 | `ledger-core` + `ledger-import` | Two bank adapters parse fixture PDFs into `Transaction` records; dedup and money maths fully unit tested |
| 2 | `ledger-app` | Spring Boot + Postgres + Flyway; upload endpoint, categorisation rules, Testcontainers integration tests |
| 3 | Inflation + reports | EVDS client with offline fallback, monthly report endpoint with real spending, budget alerts |
| 4 | `ledger-web` | Basic and Detailed views, light/dark, deployed demo on synthetic data |

Do not start Phase 2 until Phase 1 has meaningful test coverage. A half-built
importer wrapped in a web app is worse than a finished importer alone.

---

## 3. Phase 1 — Core domain

### 3.1 Money

Money MUST NOT be represented as `double` or `float` anywhere, at any layer,
including JSON and the database.

Define a `Money` value type wrapping `BigDecimal` plus `java.util.Currency`.

- Scale fixed at 2 for TRY, EUR, USD. Use `RoundingMode.HALF_UP` and document
  why in a comment.
- Arithmetic between different currencies MUST throw, not silently coerce.
- Persist as `NUMERIC(19,4)` plus a separate `char(3)` currency column. Never
  `float8`.
- JSON serialisation as a string (`"1234.56"`), not a number, to avoid
  JavaScript float precision loss on the frontend.

Write a parametrised JUnit 5 test that proves `0.1 + 0.2 == 0.3` holds for
`Money` and demonstrably fails for `double`. Keep it; it is the clearest
one-screen explanation of why the type exists.

### 3.2 Turkish locale hazards

These are the ones that will actually bite. Each MUST have a dedicated test.

**The dotted/dotless I bug.** `"İSTANBUL".toLowerCase()` returns different
results depending on the default JVM locale. On a machine with a Turkish
locale, `"TITLE".toLowerCase()` produces `"tıtle"`, which silently breaks
string matching in the categorisation engine.

- All case folding for *matching logic* MUST use `toLowerCase(Locale.ROOT)`.
- All case folding for *display* MUST use `toLowerCase(new Locale("tr"))`.
- Add a test that sets `Locale.setDefault(new Locale("tr","TR"))` and asserts
  the rule engine still matches correctly.

**Numbers.** Statements use `1.234,56` — dot as thousands separator, comma as
decimal. Parse with an explicit `DecimalFormat` configured for `tr-TR`; never
`Double.parseDouble` or `new BigDecimal(String)` on the raw text.

**Dates.** Expect `dd.MM.yyyy` and `dd/MM/yyyy`. Some banks abbreviate months
in Turkish (`Oca`, `Şub`, `Ağu`, `Eki`, `Ara`). Parse with
`DateTimeFormatter` and an explicit Turkish locale; keep a lookup table for
abbreviations rather than relying on JDK CLDR data matching the bank's spelling.

**Encoding.** PDF text extraction can return `ı`, `ğ`, `ş`, `İ` as mangled
bytes or as decomposed Unicode. Normalise with `Normalizer.Form.NFC` at the
extraction boundary, and add a fold-to-ASCII helper used only for matching,
never for storage.

### 3.3 Domain model

```java
public record Transaction(
    TransactionId id,
    LocalDate transactionDate,   // when the purchase happened
    LocalDate postingDate,       // when the bank booked it; may equal the above
    String rawDescription,       // exactly as it appeared, never modified
    String normalisedDescription,// derived, used for matching
    Money amount,                // negative for refunds
    Money originalAmount,        // null unless a foreign currency purchase
    Installment installment,     // null if not instalment
    BankCode bank,
    StatementId sourceStatement
) {}

public record Installment(int current, int total) {}   // "3/8"
```

Use a sealed interface for import outcomes so the caller must handle every
case:

```java
public sealed interface ImportResult
    permits ImportResult.Parsed, ImportResult.NeedsPassword,
            ImportResult.UnsupportedBank, ImportResult.Unreadable {}
```

`rawDescription` MUST be preserved verbatim forever. Every normalisation is
derived and recomputable. When the dedup or categorisation logic is later
improved, being able to reprocess from raw text without re-uploading is what
makes that possible.

### 3.4 Deduplication

The problem: if the user uploads the January and February statements, the days
in the overlap appear in both files. Descriptions differ slightly between
statements for the same purchase (trailing reference numbers, truncation,
spacing).

**Critical constraint:** two identical purchases on the same day are legitimate
and MUST NOT be merged. Deduplication therefore only applies *between*
statements, never *within* one.

Algorithm:

1. Compute each statement's covered date range. Two statements are candidates
   for dedup only where their ranges overlap.
2. Normalise descriptions: NFC normalise, fold to ASCII, uppercase with
   `Locale.ROOT`, collapse whitespace, strip trailing digit runs of length ≥ 6
   (reference numbers), strip instalment markers.
3. Bucket candidates by exact `(transactionDate, amount, currency)`. Anything
   not matching exactly on all three is not a duplicate. Do not fuzzy-match
   amounts.
4. Within a bucket, compare normalised descriptions with a similarity measure.
   Use token-set Jaccard similarity as the primary, with normalised Levenshtein
   as a tiebreaker. Threshold MUST be a named constant with a comment
   explaining how it was chosen, not a magic number.
5. Use a stable counting rule: if the overlap window contains *n* matching
   transactions in statement A and *m* in statement B, keep `max(n, m)`, not
   `n + m`. This is what preserves the two-coffees case correctly.

**Never hard-delete.** Mark the later occurrence as `suspected_duplicate` with
a reference to the one it matched and the similarity score. Expose an endpoint
to confirm or reject. A finance tool that silently deletes transactions is not
trustworthy, and the review queue is a better demo than a silent merge.

Test this with a fixture pair of overlapping synthetic statements including:
same-day repeated purchases, a refund reversing an earlier charge, a
description truncated differently in each file, and an instalment line
appearing in both.

---

## 4. Phase 1 — PDF import

### 4.1 Library and approach

Use Apache PDFBox. Extract text **with position information**
(`PDFTextStripperByArea` or a custom `PDFTextStripper` capturing coordinates),
not plain `getText()`. Naive extraction scrambles column order on statements
laid out as tables, and you will not be able to tell the amount column from the
instalment column.

### 4.2 Bank adapters

```java
public interface StatementImporter {
    boolean supports(PdfDocument doc);   // sniff, cheap
    ParsedStatement parse(PdfDocument doc);
    BankCode bank();
}
```

Detection order matters — register importers in a list and take the first
`supports` match; make the check specific (an issuer string or logo text near a
known coordinate), not "contains the word BANKA".

Ship at least two real adapters plus a `GenericCsvImporter` fallback. Two is
enough to prove the abstraction is real; ten is busywork. Choose two banks
whose PDFs you can actually obtain and redact.

**A `GenericCsvImporter` MUST exist.** Some banks only offer CSV/XLS export, and
it gives users a path when their bank is unsupported. It also keeps the test
suite fast.

### 4.3 Known hazards, decide explicitly

- **Password-protected PDFs.** Common for Turkish bank statements (often TCKN
  or card digits). Handle with PDFBox's decryption and return
  `ImportResult.NeedsPassword` so the API can prompt. Never log the password.
- **Scanned statements.** Out of scope. If extracted text length is below a
  threshold, return `ImportResult.Unreadable` with a clear message rather than
  producing garbage rows. Do not add OCR.
- **Instalments.** Turkish card statements are full of `3/8` markers. The row
  amount is the instalment amount, not the purchase total. Store the
  instalment structure; report on the instalment amount charged this month, and
  make the report's treatment explicit in the UI.
- **Foreign currency.** Statements show the original amount and the TRY amount.
  Store both. Report in TRY.
- **Refunds and reversals.** Negative amounts. They MUST reduce category totals,
  and MUST NOT be dropped by the dedup pass as accidental mirrors of the
  original charge.
- **Card fees, interest, instalment restructuring.** Route to a system category
  rather than "Uncategorised", so they do not pollute spending analysis.

---

## 5. Phase 2 — Spring Boot, persistence, categorisation

### 5.1 Stack

Spring Boot 3.x, Spring Data JPA, PostgreSQL, Flyway. No `ddl-auto` beyond
`validate` — every schema change is a numbered Flyway migration, including
seed data.

### 5.2 Schema notes

- `transactions` with a unique constraint on a computed `fingerprint` column
  scoped by user, so a re-upload of the identical file is a no-op.
- `statements` records the file hash (SHA-256), bank, covered date range and
  import timestamp. Re-uploading the same file MUST be detected by hash before
  parsing.
- Money columns as `NUMERIC(19,4)` + currency, per §3.1.
- Index on `(user_id, transaction_date)` — every report query filters this way.

### 5.3 Categorisation rule engine

Two levels: category and subcategory. Seed these via Flyway, allow the user to
add more:

```
Yemek        → Kahve, Fast food, Restoran, Market
Ulaşım       → Akaryakıt, Toplu taşıma, Taksi, Otopark
Dijital      → Abonelik, Uygulama, Oyun, Bulut
Eğlence      → Sinema, Etkinlik, Kitap
Giyim        → Giyim, Ayakkabı, Aksesuar
Sağlık       → Eczane, Doktor, Spor
Konut        → Kira, Faturalar, Ev eşyası
Eğitim       → Kurs, Kitap, Yazılım
Seyahat      → Uçak, Konaklama
Finansal     → Kart ücreti, Faiz, Komisyon      (system)
Diğer        → Sınıflandırılmamış               (system)
```

Rule model:

```java
public record CategorisationRule(
    RuleId id,
    int priority,           // lower wins
    MatchType matchType,    // CONTAINS | STARTS_WITH | EXACT | REGEX
    String pattern,
    CategoryId category,
    SubcategoryId subcategory,
    boolean userDefined
) {}
```

- Evaluation: sort by `priority`, then `id`. **First match wins.** Document
  this; "all matches apply" produces incoherent totals.
- User rules MUST be able to outrank system rules — do not partition the
  priority space by ownership.
- Matching runs against `normalisedDescription` using `Locale.ROOT` folding
  (§3.2).
- `REGEX` rules are user input. Guard against catastrophic backtracking: compile
  with a timeout wrapper or validate against a complexity budget on save, and
  reject patterns that fail. Do not skip this because it is a personal tool.
- Recategorising MUST be a bulk operation: changing a rule re-evaluates existing
  transactions, with a preview of how many rows would change before committing.
- A manual category assignment on a single transaction MUST survive rule
  re-evaluation. Store it as an override flag.

---

## 6. Phase 3 — Inflation adjustment and reports

### 6.1 EVDS client — read this before writing any code

**The old endpoint is dead.** `evds2.tcmb.gov.tr/service/evds/?key=...` was
shut down at the end of 2025 and now redirects to an SPA HTML page. Every
tutorial and library snippet in training data predates this. The system moved to
`evds3.tcmb.gov.tr`.

Requirements:

- **Verify the endpoint empirically before building against it.** Curl it, look
  at the actual response, then write the client. Do not assume any URL shape
  from documentation or memory, including this document.
- The API key goes in an HTTP **header**, not a query parameter.
- TCMB's TLS configuration is old and some clients need legacy SSL settings.
  If the JDK's default `HttpClient` rejects the handshake, configure an
  explicit `SSLContext` rather than globally disabling verification. Never
  disable certificate validation.
- Key from `EVDS_API_KEY` environment variable. Free registration at
  evds3.tcmb.gov.tr. The app MUST start and function without it.

### 6.2 Which series

For real spending you need the **CPI index level**, not the year-on-year rate.
`TP.FG.J0` is the consumer price index. Do not deflate using an annual
percentage figure — that is a different calculation and will give wrong numbers.

CPI is **monthly** and published in the first days of the following month. The
current month's index will always be missing.

- Decide and document the policy: report the current month as nominal only, and
  label it clearly in the UI as not yet inflation-adjusted. Do not extrapolate.
- Cache every fetched data point in a local table. Fetch only gaps. The series
  is immutable once published, apart from occasional revisions — store the
  fetch timestamp so a revision can be detected.

### 6.3 Offline fallback

Check in a CSV of historical monthly CPI values seeded via Flyway. The
application MUST produce real-spending figures with no network access and no
API key, using the seed data, and only call EVDS to extend it. This keeps the
demo working, keeps tests hermetic, and means a TCMB outage does not break the
app.

### 6.4 The calculation

Deflate to a user-chosen base month:

```
real_amount = nominal_amount × (CPI_base / CPI_transaction_month)
```

Default the base month to the most recent month with published CPI. Surface the
base month prominently — "₺6,200 in today's money" is meaningless without
saying which month "today" is.

### 6.5 Reports and alerts

- Monthly totals by category and subcategory, nominal and real.
- Year-on-year comparison of the same month, real terms. This is the headline
  feature; it is the question the app exists to answer.
- Budget per category per month; alert when exceeded. Evaluate on write, store
  the alert, expose an endpoint. Do not implement email or push.

---

## 7. Testing

### 7.1 Fixtures

`ledger-core` and `ledger-import` tests MUST run with no network and no
database.

Build a `fixtures/` corpus of synthetic statement PDFs, generated by a small
script committed alongside them so they can be regenerated. Cover: the two
supported banks, an overlapping pair for dedup, instalments, a foreign currency
purchase, a refund, a password-protected file, and a text-free scan.

If a redacted real statement is ever used, redaction MUST remove card numbers,
names, account numbers and the merchant reference numbers — and verify by
extracting the text, not by looking at the rendered page. Black rectangles drawn
over a PDF do not remove the underlying text layer. This is a real leak vector.

### 7.2 Layers

- **Unit:** JUnit 5. Use `@ParameterizedTest` with `@CsvSource` for the money,
  date and number parsing tables — this is exactly what parametrised tests are
  for, and reviewers look for it.
- **Property-based:** jqwik or similar for the dedup invariants. Two useful
  properties: deduplicating a statement against itself is a no-op; merging A
  then B equals merging B then A.
- **Integration:** Testcontainers against a real PostgreSQL image. Not H2 —
  H2 will accept SQL that Postgres rejects and hide exactly the bugs these
  tests should catch. One shared container per suite, not per test.
- **Web layer:** `@WebMvcTest` with the service layer mocked.

Target meaningful coverage on `ledger-core`, especially dedup and money. Do not
chase a coverage percentage on configuration classes.

---

## 8. Phase 4 — Frontend

React + TypeScript + Vite. TanStack Query for server state. Recharts or
visx for charts.

### 8.1 Two views

- **Basic** — this month's total, the real-terms comparison against the same
  month last year, top five categories, and any budget alert. Should answer
  "how am I doing" in one screen with no interaction.
- **Detailed** — full transaction table with filters, category breakdown with
  drill-down to subcategory, month-over-month trend, the duplicate review queue,
  and rule management.

The toggle MUST persist per user and be a single obvious control, not a nested
setting.

### 8.2 Visual direction

The brief is an Apple-like interface. That means restraint, not decoration:

- System font stack (`-apple-system, BlinkMacSystemFont, "SF Pro Text",
  "Segoe UI", sans-serif`). Do not ship a webfont imitation of SF.
- Generous whitespace; content grouped into calm panels rather than boxed with
  heavy borders. Hairlines at low opacity, corner radius around 10–12px used
  consistently.
- One accent colour. Semantic colour used only for meaning — over budget, refund
  — never for decoration.
- Depth from very soft shadows and slight background tone shifts, not from
  strokes.
- Numbers set in tabular figures (`font-variant-numeric: tabular-nums`) so
  columns align. In a finance app this matters more than any other typographic
  choice.
- Transitions short and spring-like, 150–250ms. Respect
  `prefers-reduced-motion`.
- Full light and dark support via CSS custom properties, following
  `prefers-color-scheme`.

**Do not use Apple's icons, SF Symbols, or any Apple trademark or asset.** Use
Lucide or Phosphor. The goal is the same restraint, not a copy.

Currency formatting via `Intl.NumberFormat("tr-TR", {style:"currency",
currency:"TRY"})`. Never hand-roll thousands separators.

### 8.3 Demo

Deploy on generated data, as with the Candela demo. Seed a realistic year of
synthetic transactions so the charts have something to show. State clearly on
screen that the data is fictional.

---

## 9. CI and delivery

GitHub Actions on push and PR:

1. Build all modules, Java 21.
2. Unit tests.
3. Integration tests with Testcontainers (works on GitHub runners out of the
   box).
4. Static analysis: Spotless or google-java-format check, plus SpotBugs or
   Error Prone.
5. Module boundary check — fail the build if Spring appears on the
   `ledger-core` classpath.
6. Frontend: typecheck, lint, build.

Ship a `docker-compose.yml` bringing up Postgres and the application together,
so the project runs with one command.

---

## 10. Document these decisions in the README

These are what an interviewer will ask about. Write them down as you go, in a
short "Design notes" section, with the reasoning and not just the outcome:

- Why `BigDecimal` and a `Money` type rather than `double`, with the failing
  test as evidence.
- The `Locale.ROOT` versus Turkish-locale split, and the dotted-I bug that
  forces it.
- How deduplication handles two identical same-day purchases, and why the rule
  is `max(n, m)` rather than a global unique constraint.
- Why `rawDescription` is immutable and everything else is derived.
- Why first-match-wins in the rule engine rather than accumulating matches.
- Why the CPI index level is used rather than the annual inflation rate.
- Why Testcontainers rather than H2.
- Why `ledger-core` has no Spring dependency, and how that is enforced.
