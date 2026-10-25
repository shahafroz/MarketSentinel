-- Every business table carries tenant_id: one broker firm can never read another firm's rows.
-- Money is NUMERIC(19,4) (exact), never floating point. Times are TIMESTAMPTZ (stored in UTC).

CREATE TABLE tenants (
    id                      UUID PRIMARY KEY,
    name                    TEXT        NOT NULL,
    plan                    TEXT        NOT NULL DEFAULT 'FREE',
    stripe_customer_id      TEXT,
    stripe_subscription_id  TEXT,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE users (
    id            UUID PRIMARY KEY,
    tenant_id     UUID        NOT NULL REFERENCES tenants (id),
    email         TEXT        NOT NULL UNIQUE,
    password_hash TEXT        NOT NULL,
    role          TEXT        NOT NULL,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Brokers send trades with an API key. Only a SHA-256 hash of the key is stored.
CREATE TABLE api_keys (
    id         UUID PRIMARY KEY,
    tenant_id  UUID        NOT NULL REFERENCES tenants (id),
    name       TEXT        NOT NULL,
    prefix     TEXT        NOT NULL,
    key_hash   TEXT        NOT NULL UNIQUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    revoked_at TIMESTAMPTZ
);

-- owner_ref = the beneficial owner. Accounts with the same owner count as one party (wash trades).
CREATE TABLE accounts (
    id           BIGSERIAL PRIMARY KEY,
    tenant_id    UUID        NOT NULL REFERENCES tenants (id),
    account_ref  TEXT        NOT NULL,
    owner_ref    TEXT        NOT NULL,
    account_type TEXT        NOT NULL,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (tenant_id, account_ref)
);

CREATE TABLE orders (
    id              BIGSERIAL PRIMARY KEY,
    tenant_id       UUID          NOT NULL REFERENCES tenants (id),
    order_ref       TEXT          NOT NULL,
    account_ref     TEXT          NOT NULL,
    symbol          TEXT          NOT NULL,
    side            TEXT          NOT NULL,
    quantity        BIGINT        NOT NULL CHECK (quantity > 0),
    limit_price     NUMERIC(19,4) NOT NULL CHECK (limit_price > 0),
    status          TEXT          NOT NULL,
    filled_quantity BIGINT        NOT NULL DEFAULT 0,
    placed_at       TIMESTAMPTZ   NOT NULL,
    cancelled_at    TIMESTAMPTZ,
    idempotency_key TEXT          NOT NULL,
    UNIQUE (tenant_id, order_ref),
    UNIQUE (tenant_id, idempotency_key)
);
CREATE INDEX orders_symbol_time ON orders (tenant_id, symbol, placed_at);

CREATE TABLE trades (
    id               BIGSERIAL PRIMARY KEY,
    tenant_id        UUID          NOT NULL REFERENCES tenants (id),
    trade_ref        TEXT          NOT NULL,
    symbol           TEXT          NOT NULL,
    buy_account_ref  TEXT          NOT NULL,
    sell_account_ref TEXT          NOT NULL,
    buy_order_ref    TEXT,
    sell_order_ref   TEXT,
    price            NUMERIC(19,4) NOT NULL CHECK (price > 0),
    quantity         BIGINT        NOT NULL CHECK (quantity > 0),
    executed_at      TIMESTAMPTZ   NOT NULL,
    idempotency_key  TEXT          NOT NULL,
    UNIQUE (tenant_id, trade_ref),
    UNIQUE (tenant_id, idempotency_key)
);
CREATE INDEX trades_symbol_time ON trades (tenant_id, symbol, executed_at);
CREATE INDEX trades_time ON trades (tenant_id, executed_at);

-- fingerprint makes detection re-runnable: the same finding never creates a second alert.
CREATE TABLE alerts (
    id           BIGSERIAL PRIMARY KEY,
    tenant_id    UUID        NOT NULL REFERENCES tenants (id),
    pattern      TEXT        NOT NULL,
    symbol       TEXT        NOT NULL,
    accounts     TEXT        NOT NULL,
    score        INT         NOT NULL CHECK (score BETWEEN 0 AND 100),
    title        TEXT        NOT NULL,
    reason       TEXT        NOT NULL,
    evidence     TEXT        NOT NULL,
    trade_date   DATE        NOT NULL,
    window_start TIMESTAMPTZ NOT NULL,
    window_end   TIMESTAMPTZ NOT NULL,
    status       TEXT        NOT NULL DEFAULT 'NEW',
    fingerprint  TEXT        NOT NULL,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (tenant_id, fingerprint)
);
CREATE INDEX alerts_queue ON alerts (tenant_id, status, score DESC);

CREATE TABLE cases (
    id               BIGSERIAL PRIMARY KEY,
    tenant_id        UUID        NOT NULL REFERENCES tenants (id),
    alert_id         BIGINT      NOT NULL UNIQUE REFERENCES alerts (id),
    status           TEXT        NOT NULL,
    assignee         TEXT,
    ai_draft         TEXT,
    ai_model         TEXT,
    approved_summary TEXT,
    approved_by      TEXT,
    closed_reason    TEXT,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE case_notes (
    id         BIGSERIAL PRIMARY KEY,
    tenant_id  UUID        NOT NULL REFERENCES tenants (id),
    case_id    BIGINT      NOT NULL REFERENCES cases (id),
    author     TEXT        NOT NULL,
    body       TEXT        NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE audit_events (
    id          BIGSERIAL PRIMARY KEY,
    tenant_id   UUID        NOT NULL REFERENCES tenants (id),
    actor       TEXT        NOT NULL,
    action      TEXT        NOT NULL,
    entity_type TEXT        NOT NULL,
    entity_id   TEXT        NOT NULL,
    details     TEXT        NOT NULL DEFAULT '',
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX audit_by_tenant ON audit_events (tenant_id, created_at DESC);

-- The audit trail is append-only, enforced by the database itself, not just by the application.
CREATE FUNCTION audit_events_block_change() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'audit_events is append-only';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER audit_events_no_update_delete
    BEFORE UPDATE OR DELETE ON audit_events
    FOR EACH ROW EXECUTE FUNCTION audit_events_block_change();

-- SEC EDGAR data: Form 4 insider transactions and 8-K material events.
CREATE TABLE insider_transactions (
    id               BIGSERIAL PRIMARY KEY,
    tenant_id        UUID          NOT NULL REFERENCES tenants (id),
    ticker           TEXT          NOT NULL,
    cik              TEXT          NOT NULL,
    accession        TEXT          NOT NULL,
    line_no          INT           NOT NULL,
    owner_name       TEXT          NOT NULL,
    owner_title      TEXT          NOT NULL DEFAULT '',
    transaction_date DATE          NOT NULL,
    code             TEXT          NOT NULL,
    shares           NUMERIC(19,4) NOT NULL,
    price            NUMERIC(19,4),
    acquired         BOOLEAN       NOT NULL,
    filed_on         DATE          NOT NULL,
    UNIQUE (tenant_id, accession, line_no)
);

CREATE TABLE material_events (
    id        BIGSERIAL PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES tenants (id),
    ticker    TEXT NOT NULL,
    cik       TEXT NOT NULL,
    accession TEXT NOT NULL,
    items     TEXT NOT NULL,
    filed_on  DATE NOT NULL,
    UNIQUE (tenant_id, accession)
);

-- Monthly trade counts per firm, used for plan limits.
CREATE TABLE usage_counters (
    tenant_id UUID   NOT NULL REFERENCES tenants (id),
    month     TEXT   NOT NULL,
    trades    BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (tenant_id, month)
);

-- Stripe can deliver the same webhook more than once; each event id is processed once.
CREATE TABLE processed_webhook_events (
    event_id     TEXT PRIMARY KEY,
    processed_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
