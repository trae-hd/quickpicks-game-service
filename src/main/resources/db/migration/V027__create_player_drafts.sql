CREATE TABLE player_drafts (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       TEXT NOT NULL REFERENCES tenants(id),
    round_id        UUID NOT NULL REFERENCES rounds(id),
    host_player_id  TEXT NOT NULL,
    picks_json      JSONB NOT NULL,
    tiebreaker      INT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT one_draft_per_player_per_round UNIQUE (tenant_id, round_id, host_player_id)
);

ALTER TABLE player_drafts ENABLE ROW LEVEL SECURITY;

CREATE POLICY player_drafts_tenant_isolation ON player_drafts
  FOR ALL
  USING (tenant_id = current_setting('app.current_tenant', true))
  WITH CHECK (tenant_id = current_setting('app.current_tenant', true));
