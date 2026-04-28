CREATE TABLE tenant_exclusion_catalog (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id TEXT NOT NULL REFERENCES tenants(id),
    flag_name TEXT NOT NULL,
    description TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE(tenant_id, flag_name)
);

ALTER TABLE tenant_exclusion_catalog ENABLE ROW LEVEL SECURITY;

CREATE POLICY tenant_exclusion_catalog_tenant_isolation ON tenant_exclusion_catalog
    FOR ALL
    USING (tenant_id = current_setting('app.current_tenant', true))
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true));
