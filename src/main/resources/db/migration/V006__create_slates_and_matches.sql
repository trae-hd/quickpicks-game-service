CREATE TABLE slates (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id           TEXT NOT NULL REFERENCES tenants(id),
    status              TEXT NOT NULL DEFAULT 'DRAFT',
    round_window_start  TIMESTAMPTZ NOT NULL,
    round_window_end    TIMESTAMPTZ NOT NULL,
    created_by          TEXT NOT NULL,
    approved_by         TEXT,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);

ALTER TABLE slates ENABLE ROW LEVEL SECURITY;
CREATE POLICY slates_tenant_isolation ON slates
  FOR ALL
  USING (tenant_id = current_setting('app.current_tenant', true))
  WITH CHECK (tenant_id = current_setting('app.current_tenant', true));

CREATE TABLE matches (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    slate_id                UUID NOT NULL REFERENCES slates(id),
    tenant_id               TEXT NOT NULL REFERENCES tenants(id),
    provider_match_id       TEXT NOT NULL,
    home_team               TEXT NOT NULL,
    away_team               TEXT NOT NULL,
    kick_off                TIMESTAMPTZ NOT NULL,
    league                  TEXT NOT NULL,
    regulation_result_home  INT,
    regulation_result_away  INT,
    status                  TEXT NOT NULL DEFAULT 'SCHEDULED',
    status_unknown_payload  JSONB,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT matches_provider_match_id_not_empty CHECK (provider_match_id <> '')
);

CREATE INDEX idx_matches_slate_id ON matches(slate_id);
CREATE INDEX idx_matches_provider_match_id ON matches(provider_match_id);

ALTER TABLE matches ENABLE ROW LEVEL SECURITY;
CREATE POLICY matches_tenant_isolation ON matches
  FOR ALL
  USING (tenant_id = current_setting('app.current_tenant', true))
  WITH CHECK (tenant_id = current_setting('app.current_tenant', true));
