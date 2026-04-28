-- V011__create_outbox_events.sql
CREATE TABLE outbox_events (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id VARCHAR(50) NOT NULL,
    event_type VARCHAR(100) NOT NULL,
    payload_json JSONB NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    processed_at TIMESTAMPTZ,
    error_log TEXT
);

ALTER TABLE outbox_events ENABLE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation_policy ON outbox_events
    USING (tenant_id = current_setting('app.current_tenant', true)::text);

CREATE INDEX idx_outbox_unprocessed ON outbox_events (created_at) WHERE processed_at IS NULL;
