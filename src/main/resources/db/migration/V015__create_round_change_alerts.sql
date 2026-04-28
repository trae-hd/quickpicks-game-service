CREATE TABLE round_change_alerts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    round_id UUID NOT NULL REFERENCES rounds(id),
    tenant_id TEXT NOT NULL REFERENCES tenants(id),
    alert_type TEXT NOT NULL, -- KICKOFF_CHANGE, STATUS_CHANGE
    message TEXT NOT NULL,
    severity TEXT NOT NULL DEFAULT 'MEDIUM', -- LOW, MEDIUM, HIGH
    is_resolved BOOLEAN NOT NULL DEFAULT false,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

ALTER TABLE round_change_alerts ENABLE ROW LEVEL SECURITY;

CREATE POLICY round_change_alerts_tenant_isolation ON round_change_alerts
    FOR ALL
    USING (tenant_id = current_setting('app.current_tenant', true))
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true));
