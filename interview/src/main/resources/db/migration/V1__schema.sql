CREATE TABLE wallets (
    wallet_id   varchar(16) PRIMARY KEY,
    status      varchar(10) NOT NULL CHECK (status IN ('ACTIVE', 'FROZEN')),
    balance     bigint      NOT NULL CHECK (balance >= 0),
    reserved    bigint      NOT NULL DEFAULT 0 CHECK (reserved >= 0),
    updated_at  timestamptz NOT NULL DEFAULT now(),
    CHECK (reserved <= balance)
);

CREATE SEQUENCE transfer_seq START 10000;
CREATE SEQUENCE reversal_seq START 1;
CREATE SEQUENCE payout_seq   START 1;

CREATE TABLE transfers (
    transfer_id bigint      PRIMARY KEY DEFAULT nextval('transfer_seq'),
    from_wallet varchar(16) NOT NULL REFERENCES wallets,
    to_wallet   varchar(16) NOT NULL REFERENCES wallets,
    amount      bigint      NOT NULL CHECK (amount BETWEEN 1 AND 5000000),
    reference   varchar(64) NOT NULL,
    status      varchar(12) NOT NULL DEFAULT 'COMPLETED',
    created_at  timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE reversals (
    reversal_id bigint      PRIMARY KEY DEFAULT nextval('reversal_seq'),
    transfer_id bigint      NOT NULL UNIQUE REFERENCES transfers,
    reason      varchar(255),
    created_at  timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE ledger_entries (
    entry_id      bigserial   PRIMARY KEY,
    wallet_id     varchar(16) NOT NULL REFERENCES wallets,
    type          varchar(20) NOT NULL,
    amount        bigint      NOT NULL,
    balance_after bigint      NOT NULL CHECK (balance_after >= 0),
    reference     varchar(64) NOT NULL,
    created_at    timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX ledger_entries_wallet_idx ON ledger_entries (wallet_id, entry_id DESC);

CREATE TABLE payouts (
    payout_id       varchar(32) PRIMARY KEY,
    wallet_id       varchar(16) NOT NULL REFERENCES wallets,
    bank_account    varchar(64) NOT NULL,
    amount          bigint      NOT NULL CHECK (amount > 0),
    state           varchar(12) NOT NULL,
    bank_ref        varchar(64),
    conflict        boolean     NOT NULL DEFAULT false,
    attempts        int         NOT NULL DEFAULT 0,
    next_attempt_at timestamptz NOT NULL DEFAULT now(),
    lease_owner     varchar(64),
    lease_until     timestamptz,
    created_at      timestamptz NOT NULL DEFAULT now(),
    updated_at      timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX payouts_work_idx ON payouts (next_attempt_at) WHERE state NOT IN ('SUCCEEDED', 'FAILED');

CREATE TABLE bank_callbacks (
    event_id    varchar(64) PRIMARY KEY,
    payout_id   varchar(32) NOT NULL,
    status      varchar(10) NOT NULL,
    received_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE idempotency_keys (
    idem_key        varchar(64)  PRIMARY KEY,
    request_hash    varchar(64)  NOT NULL,
    state           varchar(12)  NOT NULL,   -- IN_PROGRESS | DONE
    response_status int,
    response_body   text,
    created_at      timestamptz  NOT NULL DEFAULT now(),
    expires_at      timestamptz  NOT NULL
);

CREATE TABLE b2c_batches (
    batch_id      varchar(64) PRIMARY KEY,
    source_wallet varchar(16) NOT NULL,
    rows_hash     varchar(64) NOT NULL,
    status        varchar(12) NOT NULL,
    total_rows    int         NOT NULL,
    created_at    timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE b2c_rows (
    batch_id  varchar(64) NOT NULL REFERENCES b2c_batches,
    seq       int         NOT NULL,
    row_id    varchar(64) NOT NULL,
    to_wallet varchar(16) NOT NULL,
    amount    bigint      NOT NULL,
    result    varchar(24),
    PRIMARY KEY (batch_id, seq)
);
