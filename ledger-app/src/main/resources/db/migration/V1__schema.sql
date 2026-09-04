-- Phase 2 schema. SPEC §5.2.
--
-- Single-user for now: every row carries user_id, and the application supplies a fixed
-- id until authentication exists. The column is here from the start because the
-- constraints that matter -- the fingerprint uniqueness and the reporting index -- are
-- scoped by user, and retrofitting a scope onto a unique constraint later is a data
-- migration rather than an added column.

create table statements (
    id                uuid         primary key,
    user_id           uuid         not null,
    bank              varchar(32)  not null,
    source_file_name  varchar(512) not null,
    -- SHA-256 of the uploaded bytes, as hex. Re-uploading the identical file is detected
    -- here, before the parser is ever invoked. varchar rather than char: a hash is always
    -- 64 characters, so char's blank padding buys nothing and only invites a comparison
    -- that silently ignores trailing spaces.
    content_hash      varchar(64)  not null,
    period_start      date         not null,
    period_end        date         not null,
    imported_at       timestamptz  not null default now(),

    constraint statements_period_ordered check (period_end >= period_start),
    constraint statements_user_hash_unique unique (user_id, content_hash)
);

create index statements_user_period_idx on statements (user_id, period_start, period_end);

create table categories (
    id           varchar(64)  primary key,
    display_name varchar(128) not null,
    -- System categories cannot be deleted; they are where fees and the unclassified
    -- remainder are routed, and code depends on them existing.
    is_system    boolean      not null default false,
    sort_order   integer      not null default 0
);

create table subcategories (
    id           varchar(128) primary key,
    category_id  varchar(64)  not null references categories (id),
    display_name varchar(128) not null,
    sort_order   integer      not null default 0
);

create index subcategories_category_idx on subcategories (category_id);

create table transactions (
    id                    uuid         primary key,
    user_id               uuid         not null,
    statement_id          uuid         not null references statements (id),

    transaction_date      date         not null,
    posting_date          date         not null,

    -- Exactly as the bank printed it, never modified. Everything below that is derived
    -- from it is recomputable, which is what allows a better normaliser or rule set to be
    -- applied to existing history without a re-upload (SPEC §3.3).
    raw_description       text         not null,
    normalised_description text        not null,

    -- Money is NUMERIC(19,4) plus a currency code, never float8 (SPEC §3.1).
    amount                numeric(19, 4) not null,
    currency              char(3)      not null,
    original_amount       numeric(19, 4),
    original_currency     char(3),

    installment_current   integer,
    installment_total     integer,

    bank                  varchar(32)  not null,

    -- Identifies one row of one statement file: stable across a re-upload of the same
    -- file, and distinct for two identical purchases on the same day (SPEC §3.4).
    fingerprint           varchar(64)  not null,

    category_id           varchar(64)  references categories (id),
    subcategory_id        varchar(128) references subcategories (id),
    -- A category the user set by hand. Rule re-evaluation must not undo it.
    category_override     boolean      not null default false,

    duplicate_status      varchar(16)  not null default 'NONE',
    duplicate_of_id       uuid         references transactions (id),
    duplicate_similarity  numeric(5, 4),
    duplicate_reason      varchar(32),

    created_at            timestamptz  not null default now(),

    constraint transactions_user_fingerprint_unique unique (user_id, fingerprint),
    constraint transactions_installment_pair
        check ((installment_current is null) = (installment_total is null)),
    constraint transactions_installment_range
        check (installment_total is null
               or (installment_total >= 1 and installment_current between 1 and installment_total)),
    -- An original amount is what a foreign purchase cost in its own currency; repeating
    -- the settled currency there would be meaningless.
    constraint transactions_original_pair
        check ((original_amount is null) = (original_currency is null)),
    constraint transactions_original_currency_differs
        check (original_currency is null or original_currency <> currency),
    constraint transactions_duplicate_status
        check (duplicate_status in ('NONE', 'SUSPECTED', 'CONFIRMED', 'REJECTED')),
    constraint transactions_duplicate_pointer
        check ((duplicate_status = 'NONE') = (duplicate_of_id is null)),
    constraint transactions_not_own_duplicate check (duplicate_of_id <> id)
);

-- Every report filters this way (SPEC §5.2).
create index transactions_user_date_idx on transactions (user_id, transaction_date);
create index transactions_statement_idx on transactions (statement_id);
-- The review queue is a small slice of a large table, so it gets a partial index.
create index transactions_review_queue_idx on transactions (user_id)
    where duplicate_status = 'SUSPECTED';

create table categorisation_rules (
    id             uuid         primary key,
    user_id        uuid         not null,
    -- Lower wins. The space is deliberately not partitioned by ownership: a user rule
    -- may be given priority 1 and outrank every seeded rule (SPEC §5.3).
    priority       integer      not null,
    match_type     varchar(16)  not null,
    pattern        varchar(200) not null,
    category_id    varchar(64)  not null references categories (id),
    subcategory_id varchar(128) references subcategories (id),
    user_defined   boolean      not null default true,
    created_at     timestamptz  not null default now(),

    constraint categorisation_rules_match_type
        check (match_type in ('CONTAINS', 'STARTS_WITH', 'EXACT', 'REGEX')),
    constraint categorisation_rules_pattern_not_blank check (btrim(pattern) <> '')
);

-- Evaluation order: priority, then id as a total tiebreak.
create index categorisation_rules_order_idx on categorisation_rules (user_id, priority, id);
