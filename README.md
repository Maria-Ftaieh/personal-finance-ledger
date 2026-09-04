# Personal Finance Ledger

Imports Turkish credit card statements from PDF, deduplicates transactions across
overlapping statements, categorises spending with a user-editable rule engine, and
reports nominal and inflation-adjusted spending.

Built against [SPEC.md](SPEC.md), one phase at a time.

| Phase | Module | Status |
|---|---|---|
| 1 | `ledger-core`, `ledger-import` | **done** |
| 2 | `ledger-app` — Spring Boot, Postgres, Flyway | **done** |
| 3 | Inflation adjustment and reports | not started |
| 4 | `ledger-web` | not started |

---

## Running it

The whole thing, database included:

```bash
docker compose up --build
```

The API is then on `http://localhost:8080/api`.

To build and test, Java 21 or newer and Maven 3.9+:

```bash
mvn verify
```

`verify` runs the module boundary check, the unit, property and Testcontainers
integration tests, the `google-java-format` check and SpotBugs.
`mvn spotless:apply` fixes formatting.

The PostgreSQL integration tests need a Docker daemon and **skip themselves**
when there is none, so a green local build on a machine without Docker has not
run them. CI checks for Docker before the build and fails if any test was
skipped, so they cannot pass unnoticed there.

To run the application against your own PostgreSQL instead of Compose:

```bash
LEDGER_DB_URL=jdbc:postgresql://localhost:5432/ledger \
LEDGER_DB_USER=ledger LEDGER_DB_PASSWORD=ledger \
mvn -pl ledger-app spring-boot:run
```

### Regenerating the fixture corpus

No real statement has ever been in this repository. Every fixture is synthetic and
produced by a committed generator, so the corpus can be rebuilt or extended without
anyone needing a real PDF:

```bash
mvn install -DskipTests
mvn -pl ledger-import org.codehaus.mojo:exec-maven-plugin:3.1.0:java \
    -Dexec.mainClass=dev.ledger.imports.fixtures.FixtureGenerator \
    -Dexec.classpathScope=test \
    -Dexec.args=ledger-import/src/test/resources/fixtures
```

A pre-commit hook (`git config core.hooksPath .githooks`, already set) rejects any
staged PDF outside that fixture directory.

---

## Phase 1 — what is in it

**`ledger-core`** — the domain, with no dependencies at all. `Money`, the Turkish
locale and parsing helpers, the deduplication algorithm, and the categorisation rule
engine.

**`ledger-import`** — PDF text extraction with coordinates, two bank adapters
(Garanti BBVA and Yapı Kredi), a generic CSV fallback, and the service that sniffs a
file and returns one of four outcomes.

```java
ImportResult result = new StatementImportService().importFile(bytes, "ekstre.pdf", null);

switch (result) {
  case ImportResult.Parsed p          -> store(p.statement());
  case ImportResult.NeedsPassword n   -> promptForPassword(n.fileName());
  case ImportResult.UnsupportedBank u -> offerCsvUpload(u.fileName());
  case ImportResult.Unreadable u      -> explain(u.reason());
}
```

---

## Phase 2 — what is in it

**`ledger-app`** — Spring Boot, PostgreSQL, Flyway. Three migrations: the schema,
the Turkish category taxonomy from §5.3, and a seeded rule set. `ddl-auto` is
`validate` and nothing else, so a mapping that disagrees with the schema stops the
application at startup rather than at the first query.

| | |
|---|---|
| `POST /api/statements` | upload a PDF or CSV; optional `password` |
| `GET /api/statements` | what has been imported |
| `GET /api/transactions` | `from`, `to`, `categoryId`, `includeConfirmedDuplicates` |
| `PUT /api/transactions/{id}/category` | assign a category by hand |
| `DELETE /api/transactions/{id}/category` | drop the manual assignment |
| `GET /api/duplicates` | the review queue |
| `POST /api/duplicates/{id}/confirm` \| `/reject` | decide one |
| `GET /api/rules`, `POST`, `PUT /{id}`, `DELETE /{id}` | rule management |
| `GET /api/rules/preview` | how many rows a re-evaluation would move |
| `POST /api/rules/reevaluate` | commit it |
| `GET /api/categories` | the two-level taxonomy |

An upload answers with a `status` rather than a bare error, because the four
outcomes need four different things from the user:

```
201 IMPORTED          { statementId, transactionsImported, suspectedDuplicates }
200 ALREADY_IMPORTED  the same bytes were uploaded before; nothing changed
422 NEEDS_PASSWORD    prompt, retry with ?password=
422 UNSUPPORTED_BANK  offer the CSV path
422 UNREADABLE        a scan, or not a statement at all
```

---

## Design notes

### Money is a type, not a `double`

`0.1` has no exact binary representation, so a price held in a `double` is wrong
before any arithmetic happens and the error compounds with every addition.
`MoneyPrecisionTest` puts the two side by side: `Money` adds a hundred ten-kuruş
items to exactly ₺10.00, the `double` does not. `Money` wraps a `BigDecimal` and a
`Currency`, fixes the scale at 2 in its canonical constructor — so `1.5` and `1.50`
are one value and the record's generated `equals` behaves as a reader expects — and
throws on cross-currency arithmetic rather than coercing. Rounding is `HALF_UP`
because that is what the statement in the user's hand did.

It crosses the JSON boundary as a string. A JSON number becomes a JavaScript double
in the browser and loses the precision the backend just protected.

### `Locale.ROOT` for matching, Turkish for display

Turkish pairs its four i letters as `i↔İ` and `ı↔I`, unlike every other locale. On a
machine whose default locale is `tr-TR` — the likely machine for this application —
`"TITLE".toLowerCase()` returns `"tıtle"`, and every string comparison written with
the no-argument overload silently stops matching.

So the split is absolute: matching logic folds with `Locale.ROOT`, display folds with
`tr-TR`. `TurkishTextTest` asserts the bug exists and that the helpers are immune to
it, and `RuleEngineTest` sets the default locale to `tr-TR` and checks the rule engine
still categorises correctly.

The same section covers the other three hazards that actually bite: statement numbers
are `1.234,56` and are parsed with an explicit `DecimalFormat` (`new BigDecimal("1.234")`
succeeds and is wrong by a factor of a thousand); dates come as `dd.MM.yyyy` or with
an abbreviated Turkish month, resolved through a table we own rather than JDK CLDR
data that moves between releases; and extracted text is NFC-normalised at the
extraction boundary so composed and decomposed `ş` compare equal.

### `rawDescription` is immutable, everything else is derived

The bank's text is stored exactly as printed and never touched.
`normalisedDescription`, the dedup comparison and the assigned category are all
recomputed from it. That is what makes it possible to improve the normaliser or the
rule set later and reprocess years of history without asking the user to re-upload
anything.

### Deduplication keeps `max(n, m)`

Consecutive statements share the days between one period's end and the next one's
start, so those purchases are printed twice. The constraint that shapes everything is
that **two identical purchases on the same day are legitimate** — two coffees from the
same shop for the same price is an ordinary Tuesday, and a unique constraint on
`(date, amount, description)` would erase one of them permanently.

So dedup runs only *between* statements, never within one, and it is a pairing
problem rather than a uniqueness problem. Each line in the later statement claims at
most one unclaimed line in the earlier one, which leaves `n + m - min(n, m) = max(n, m)`:
the larger of the two accounts of the same days. Three overlapping statements of one
purchase still collapse to one, because a claim is scoped to the statement making it.

Candidates must match **exactly** on date, amount and currency; the sign is part of
the amount, so a refund never lands in the same bucket as the charge it reverses.
Only then are descriptions compared, with token-set Jaccard above 0.60 — chosen
against the corpus, where a repeat that drifts by one token scores 0.67 and two
unrelated merchants sharing a generic token score 0.33 — plus a narrow prefix rule
for the one difference a set measure cannot see, a description truncated at two
different column widths.

**Nothing is deleted.** The later occurrence is reported with its score and the reason
so it can be flagged `suspected_duplicate` and reviewed. A finance tool that silently
drops rows is not one you can trust with the rows it kept.

### First match wins in the rule engine

Rules are sorted by priority, then id, and the first match decides. Letting every
matching rule contribute produces totals that do not add up: a coffee that is both
"Kahve" and "Restoran" would be counted twice in the category breakdown and the
monthly total would exceed what was actually spent. The priority space is not
partitioned by ownership either — a user rule can be given priority 1 and outrank
every seeded rule, because overriding a bad guess is the thing users most want to do.

User-authored regular expressions get two defences: a complexity budget at save time
that refuses a quantifier nested inside a quantified group, and a step budget at match
time implemented as a `CharSequence` that counts reads and gives up. Java's regex
engine has no timeout of its own, and a wedged thread with no error is far harder to
diagnose than a rejected rule.

### PDF text is extracted with coordinates

`PDFTextStripper.getText()` flattens a table into a stream of words whose column is no
longer recoverable — the amount and the instalment count come out adjacent and
indistinguishable. Keeping the x coordinate lets `ColumnLayout` derive each column
from the header row: a column starts where its header label starts and runs until the
next one begins. Anchoring on the left edge rather than the midpoint between labels is
what lets a 200-point description column live under the single word "Açıklama".

Two adapters, not ten. Two is enough to prove the abstraction is real, and the layouts
differ in every way that matters: five columns against three, `dd.MM.yyyy` against
`05 Oca 2026`, a separate posting-date column against none, and a trailing minus for
refunds against a leading one. A `GenericCsvImporter` exists because some banks only
export CSV, and because it gives users a path when their bank is unsupported.

### The fingerprint, and why it is not the deduplication key

§5.2 wants a unique constraint on a computed fingerprint so that re-uploading a
file is a no-op. Taken naively that constraint is in direct conflict with §3.4: a
fingerprint over `(date, amount, description)` collides for two coffees bought on
the same morning, and the second insert — a real purchase — would be rejected.

The two are reconciled by making the fingerprint identify **a row of a document**
rather than a purchase. It is a hash of the statement's own content hash, the row's
values, and an occurrence number counting identical rows within that file. The same
bytes always produce the same fingerprints, so a re-upload is idempotent; two
identical lines in one file get different ones, so both survive.

Recognising the same purchase printed on two *different* documents is a separate
problem with a separate answer. It is scored, it is reviewable, and it is
deliberately not a database constraint — a unique index cannot express "probably the
same thing, ask the user", and a finance tool needs to be able to say that.

The statement's SHA-256 is checked before the parser runs, so an identical
re-upload costs one indexed query rather than a full parse.

### Categorising: first match wins, and the user always wins

The engine is the one from `ledger-core` — the app layer only loads rules, hands
them over, and writes the verdict back. Two rules in the schema make it behave the
way §5.3 asks:

Nothing reserves a priority band for seeded rules. A user rule can take priority 1
and outrank all sixty-four of them, because overriding a bad guess is the thing
users most want to do, and a partitioned priority space makes it impossible.

A category the user sets by hand is stored as an override flag, not as a new rule.
Inventing a rule from one correction would silently recategorise everything that
happens to look similar. The flag means re-evaluation skips the row, which is what
"a manual assignment must survive rule re-evaluation" actually requires.

Bulk recategorisation is two calls, `preview` then `reevaluate`, because editing one
rule can reshape a year of history and a number to look at first is the difference
between a deliberate change and an accident.

Anything no rule matches lands in the seeded `Diğer → Sınıflandırılmamış`, and card
fees, interest and instalment restructuring are routed to `Finansal` by seeded rules
so they never masquerade as spending (§4.3).

### Testcontainers, not H2

H2 would accept most of `V1__schema.sql` and quietly ignore the parts that matter:
the partial index on the review queue, `numeric(19, 4)`, the check constraints that
keep an instalment's two columns in step, `gen_random_uuid()` in the seed. The
migrations *are* a large part of what Phase 2 is, so testing them against anything
other than PostgreSQL would be testing a different application.

One container is shared by the whole suite and Spring's context cache keeps the
application context alive alongside it.

### A known behaviour: deduplication crosses banks

The pass compares any two statements whose periods overlap, including statements
from different banks. An instalment charged to two different cards on the same day
for the same amount will be flagged. That is the correct default — it is far more
often one purchase seen twice than two identical ones — and it is why the outcome is
a review queue rather than a merge. Rejecting a match is one call, and a rejected
match is never raised again.

### Enforcing the module boundary

`ledger-core` has no dependencies and `ledger-import` has one (PDFBox). Maven Enforcer
bans Spring, Spring Data, JPA and Hibernate from both, transitively, and CI runs it on
every push. `ledger-app` replaces that rule set rather than adding to it — the one
module Spring belongs in — which is what `combine.self="override"` in its POM does.

The point of the boundary is that the domain logic is testable without a container:
the 128 tests in `ledger-core` need no network, no database and no application
context, and the money, locale, dedup and rule-engine logic they cover is the same
code the web application runs.

