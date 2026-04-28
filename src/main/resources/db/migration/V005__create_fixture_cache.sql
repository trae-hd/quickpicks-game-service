CREATE TABLE fixture_cache (
    provider_match_id   TEXT PRIMARY KEY,
    provider_id         TEXT NOT NULL REFERENCES feed_providers(id),
    league_id           TEXT NOT NULL,
    league_name         TEXT NOT NULL,
    home_team           TEXT NOT NULL,
    away_team           TEXT NOT NULL,
    kick_off            TIMESTAMPTZ NOT NULL,
    raw_payload         JSONB NOT NULL,
    expires_at          TIMESTAMPTZ NOT NULL,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_fixture_cache_league_kickoff ON fixture_cache(league_id, kick_off);
CREATE INDEX idx_fixture_cache_expires_at ON fixture_cache(expires_at);

-- fixture_cache is NOT tenant-scoped — it is a shared global cache.
