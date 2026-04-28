-- Host casino player feed integration (addendum V2)
-- Adds provenance columns to tenant_player_access so manual CSV entries and
-- host-feed-synced entries can coexist and be distinguished.

ALTER TABLE tenant_player_access
    ADD COLUMN IF NOT EXISTS source       TEXT        NOT NULL DEFAULT 'MANUAL',
    ADD COLUMN IF NOT EXISTS updated_by   TEXT,
    ADD COLUMN IF NOT EXISTS reason       TEXT,
    ADD COLUMN IF NOT EXISTS synced_at    TIMESTAMPTZ;

COMMENT ON COLUMN tenant_player_access.source IS 'MANUAL = operator CSV upload, HOST_FEED = host casino API sync';

-- Player exclusion event log — captures self-exclusion pushes from the host casino
-- between entry placement and settlement. Entries are not voided automatically;
-- the table feeds an operator review queue.
CREATE TABLE IF NOT EXISTS player_exclusion_events (
    id               UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id        TEXT        NOT NULL REFERENCES tenants(id),
    host_player_id   TEXT        NOT NULL,
    exclusion_type   TEXT        NOT NULL,
    effective_at     TIMESTAMPTZ NOT NULL,
    reason           TEXT,
    entries_flagged  INT         NOT NULL DEFAULT 0,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now()
);

ALTER TABLE player_exclusion_events ENABLE ROW LEVEL SECURITY;

CREATE POLICY player_exclusion_events_tenant_isolation ON player_exclusion_events
    FOR ALL
    USING  (tenant_id = current_setting('app.current_tenant', true))
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true));

-- Tenant-level feed toggle and HMAC config
ALTER TABLE tenants
    ADD COLUMN IF NOT EXISTS player_feed_enabled      BOOLEAN     NOT NULL DEFAULT false,
    ADD COLUMN IF NOT EXISTS player_feed_hmac_secret  TEXT,
    ADD COLUMN IF NOT EXISTS player_feed_last_sync_at TIMESTAMPTZ;