CREATE TABLE tenant_player_access (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id TEXT NOT NULL REFERENCES tenants(id),
    player_id TEXT NOT NULL,
    access_level TEXT NOT NULL, -- ALLOW, BLOCK
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE(tenant_id, player_id)
);

ALTER TABLE tenant_player_access ENABLE ROW LEVEL SECURITY;

CREATE POLICY tenant_player_access_tenant_isolation ON tenant_player_access
    FOR ALL
    USING (tenant_id = current_setting('app.current_tenant', true))
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true));
