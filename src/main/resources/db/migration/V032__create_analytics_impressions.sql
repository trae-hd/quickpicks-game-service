CREATE TABLE analytics_impressions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id TEXT NOT NULL REFERENCES tenants(id),
    round_id UUID NOT NULL REFERENCES rounds(id),
    host_player_id TEXT NOT NULL,
    impression_type TEXT NOT NULL DEFAULT 'POST_ENTRY_RECEIPT',
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_analytics_impressions_round ON analytics_impressions(tenant_id, round_id);

CREATE UNIQUE INDEX idx_analytics_impressions_dedup
    ON analytics_impressions(tenant_id, round_id, host_player_id, impression_type);

ALTER TABLE analytics_impressions ENABLE ROW LEVEL SECURITY;

CREATE POLICY analytics_impressions_tenant_isolation ON analytics_impressions
    FOR ALL
    USING (tenant_id = current_setting('app.current_tenant', true))
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true));
