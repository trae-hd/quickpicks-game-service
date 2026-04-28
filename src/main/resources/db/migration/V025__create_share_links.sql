CREATE TABLE share_links (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id TEXT NOT NULL,
    player_id TEXT NOT NULL,
    entry_id UUID NOT NULL REFERENCES entries(id),
    token TEXT NOT NULL UNIQUE,
    picks_json JSONB NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    expires_at TIMESTAMPTZ NOT NULL
);

ALTER TABLE share_links ENABLE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation_policy ON share_links
    USING (tenant_id = current_setting('app.current_tenant', true));

CREATE INDEX idx_share_links_token ON share_links(token);
CREATE INDEX idx_share_links_tenant_player ON share_links(tenant_id, player_id);
