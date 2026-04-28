

CREATE TABLE player_promotions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id TEXT NOT NULL REFERENCES tenants(id),
    player_id TEXT NOT NULL,
    type TEXT NOT NULL, -- FREE_ENTRY
    status TEXT NOT NULL DEFAULT 'ACTIVE', -- ACTIVE, CONSUMED, EXPIRED
    round_id UUID REFERENCES rounds(id), -- Specific round if bound
    granted_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    consumed_at TIMESTAMPTZ,
    expires_at TIMESTAMPTZ
);

ALTER TABLE player_promotions ENABLE ROW LEVEL SECURITY;

CREATE POLICY player_promotions_tenant_isolation ON player_promotions
    FOR ALL
    USING (tenant_id = current_setting('app.current_tenant', true))
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true));
