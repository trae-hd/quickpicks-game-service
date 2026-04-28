CREATE TABLE wallet_transactions (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id           TEXT NOT NULL REFERENCES tenants(id),
    player_id           TEXT NOT NULL,
    round_id            UUID REFERENCES rounds(id),
    entry_id            UUID REFERENCES entries(id),
    type                TEXT NOT NULL, -- DEBIT, CREDIT, REFUND, PROMOTION
    amount_pence        BIGINT NOT NULL,
    currency            TEXT NOT NULL,
    provider_tx_id      TEXT UNIQUE, -- The ID returned by the host casino wallet
    idempotency_key     TEXT NOT NULL, -- Our unique key sent to the host
    status              TEXT NOT NULL DEFAULT 'PENDING', -- PENDING, SUCCESS, FAILED
    error_code          TEXT,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_wallet_tx_player_id ON wallet_transactions(player_id);
CREATE INDEX idx_wallet_tx_tenant_id ON wallet_transactions(tenant_id);
CREATE INDEX idx_wallet_tx_entry_id ON wallet_transactions(entry_id);

ALTER TABLE wallet_transactions ENABLE ROW LEVEL SECURITY;

CREATE POLICY wallet_transactions_tenant_isolation ON wallet_transactions
  FOR ALL
  USING (tenant_id = current_setting('app.current_tenant', true))
  WITH CHECK (tenant_id = current_setting('app.current_tenant', true));

CREATE TABLE trending_snapshots (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    round_id            UUID NOT NULL REFERENCES rounds(id),
    tenant_id           TEXT NOT NULL REFERENCES tenants(id),
    snapshot_data       JSONB NOT NULL, -- { "match_id": { "HOME": count, "AWAY": count, "DRAW": count } }
    is_frozen           BOOLEAN NOT NULL DEFAULT false,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_trending_snapshots_round_tenant ON trending_snapshots(round_id, tenant_id);

ALTER TABLE trending_snapshots ENABLE ROW LEVEL SECURITY;

CREATE POLICY trending_snapshots_tenant_isolation ON trending_snapshots
  FOR ALL
  USING (tenant_id = current_setting('app.current_tenant', true))
  WITH CHECK (tenant_id = current_setting('app.current_tenant', true));
