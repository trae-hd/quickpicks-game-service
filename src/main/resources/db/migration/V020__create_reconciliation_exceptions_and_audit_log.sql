-- V020__create_reconciliation_exceptions_and_audit_log.sql
CREATE TABLE reconciliation_exceptions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id VARCHAR(50) NOT NULL REFERENCES tenants(id),
    host_txn_id VARCHAR(100),
    player_id VARCHAR(100) NOT NULL,
    transaction_type VARCHAR(50) NOT NULL,
    expected_amount_pence BIGINT NOT NULL,
    actual_amount_pence BIGINT,
    exception_type VARCHAR(50) NOT NULL, -- MISSING_ON_HOST, MISSING_ON_US, AMOUNT_MISMATCH
    status VARCHAR(20) NOT NULL DEFAULT 'OPEN', -- OPEN, RESOLVED
    resolved_at TIMESTAMPTZ,
    resolved_by VARCHAR(100),
    notes TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

ALTER TABLE reconciliation_exceptions ENABLE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation_policy ON reconciliation_exceptions
    USING (tenant_id = current_setting('app.current_tenant', true)::text);

CREATE TABLE audit_log (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id VARCHAR(50) NOT NULL REFERENCES tenants(id),
    actor_id VARCHAR(100) NOT NULL, -- playerId or operatorId
    action VARCHAR(100) NOT NULL,
    target_id VARCHAR(100), -- roundId, entryId, etc.
    payload JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

ALTER TABLE audit_log ENABLE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation_policy ON audit_log
    USING (tenant_id = current_setting('app.current_tenant', true)::text);

CREATE INDEX idx_reconciliation_tenant_status ON reconciliation_exceptions (tenant_id, status);
CREATE INDEX idx_audit_log_tenant_action ON audit_log (tenant_id, action);
