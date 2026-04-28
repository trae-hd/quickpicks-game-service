CREATE TABLE player_profile_counters (
    tenant_id TEXT NOT NULL REFERENCES tenants(id),
    player_id TEXT NOT NULL,
    counters_json JSONB NOT NULL DEFAULT '{}',
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (tenant_id, player_id)
);

ALTER TABLE player_profile_counters ENABLE ROW LEVEL SECURITY;

CREATE POLICY player_profile_counters_tenant_isolation ON player_profile_counters
    FOR ALL
    USING (tenant_id = current_setting('app.current_tenant', true))
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true));
