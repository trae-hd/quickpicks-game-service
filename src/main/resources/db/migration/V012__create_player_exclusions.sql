-- V012__create_player_exclusions.sql
CREATE TABLE player_exclusions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id VARCHAR(50) NOT NULL,
    player_id VARCHAR(100) NOT NULL,
    reason TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE(tenant_id, player_id)
);

ALTER TABLE player_exclusions ENABLE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation_policy ON player_exclusions
    USING (tenant_id = current_setting('app.current_tenant', true)::text);
