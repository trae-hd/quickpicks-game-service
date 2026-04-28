CREATE TABLE entries (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    round_id            UUID NOT NULL REFERENCES rounds(id),
    tenant_id           TEXT NOT NULL REFERENCES tenants(id),
    player_id           TEXT NOT NULL,
    picks_json          JSONB NOT NULL,
    stake_pence         BIGINT NOT NULL,
    currency            TEXT NOT NULL,
    is_free_entry       BOOLEAN NOT NULL DEFAULT false,
    status              TEXT NOT NULL DEFAULT 'PENDING',
    transaction_id      TEXT,
    share_token         TEXT,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_entries_round_id ON entries(round_id);
CREATE INDEX idx_entries_player_id ON entries(player_id);
CREATE INDEX idx_entries_tenant_player ON entries(tenant_id, player_id);

ALTER TABLE entries ENABLE ROW LEVEL SECURITY;
CREATE POLICY entries_tenant_isolation ON entries
  FOR ALL
  USING (tenant_id = current_setting('app.current_tenant', true))
  WITH CHECK (tenant_id = current_setting('app.current_tenant', true));

CREATE TABLE entry_results (
    entry_id            UUID PRIMARY KEY REFERENCES entries(id),
    tenant_id           TEXT NOT NULL REFERENCES tenants(id),
    correct_picks       INT NOT NULL DEFAULT 0,
    is_jackpot_winner   BOOLEAN NOT NULL DEFAULT false,
    prize_pence         BIGINT NOT NULL DEFAULT 0,
    settled_at          TIMESTAMPTZ,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);

ALTER TABLE entry_results ENABLE ROW LEVEL SECURITY;
CREATE POLICY entry_results_tenant_isolation ON entry_results
  FOR ALL
  USING (tenant_id = current_setting('app.current_tenant', true))
  WITH CHECK (tenant_id = current_setting('app.current_tenant', true));
