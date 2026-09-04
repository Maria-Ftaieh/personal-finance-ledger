-- Phase 3: inflation adjustment, reports and budget alerts. SPEC §6.

-- Every CPI level the application has ever seen, wherever it came from.
--
-- SPEC §6.2: fetch only gaps, and keep the fetch timestamp so a revision can be detected.
-- TUIK does occasionally revise a published month; because the value and the timestamp
-- are stored together, a later fetch that disagrees with a cached one is visible rather
-- than silently overwriting history.
create table cpi_observations (
    series_code varchar(64)    not null,
    -- Always the first day of the month. CPI is monthly; storing a real date rather than
    -- a year/month pair keeps the range queries plain SQL.
    month       date           not null,
    -- The index LEVEL, never an annual rate (SPEC §6.2). Six decimals because the source
    -- publishes two and a rebased series may publish more.
    index_value numeric(19, 6) not null,
    source      varchar(16)    not null,
    fetched_at  timestamptz    not null default now(),

    primary key (series_code, month),
    constraint cpi_month_is_first_of_month check (extract(day from month) = 1),
    constraint cpi_index_positive check (index_value > 0),
    -- SEED is the checked-in CSV, EVDS is the live feed.
    constraint cpi_source_known check (source in ('SEED', 'EVDS'))
);

-- A recurring monthly limit for a category. SPEC §6.5 asks for a budget per category per
-- month; one row per category that applies to every month is the smaller thing that does
-- it, and a month-specific override can be added later without moving the alerts.
create table budgets (
    id          uuid           primary key,
    user_id     uuid           not null,
    category_id varchar(64)    not null references categories (id),
    amount      numeric(19, 4) not null,
    currency    char(3)        not null,
    created_at  timestamptz    not null default now(),

    constraint budgets_user_category_unique unique (user_id, category_id),
    constraint budgets_amount_positive check (amount > 0)
);

-- A breach that has already happened, evaluated on write and stored (SPEC §6.5).
-- No email, no push: the alert is a row and an endpoint.
create table budget_alerts (
    id            uuid           primary key,
    user_id       uuid           not null,
    category_id   varchar(64)    not null references categories (id),
    month         date           not null,
    budget_amount numeric(19, 4) not null,
    spent_amount  numeric(19, 4) not null,
    currency      char(3)        not null,
    raised_at     timestamptz    not null default now(),

    -- One standing alert per category per month: re-evaluating updates the figures rather
    -- than piling up a row per import.
    constraint budget_alerts_unique unique (user_id, category_id, month),
    constraint budget_alerts_month_is_first check (extract(day from month) = 1)
);

create index budget_alerts_user_month_idx on budget_alerts (user_id, month);
