CREATE TABLE feed_league_mappings (
    id                   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    provider_id          TEXT NOT NULL REFERENCES feed_providers(id),
    provider_league_id   TEXT NOT NULL,
    league_name          TEXT NOT NULL,
    country              TEXT,
    created_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE(provider_id, provider_league_id)
);

-- feed_league_mappings is NOT tenant-scoped.
-- Provides a canonical registry mapping provider-specific league IDs to display names.
-- When switching feed providers, add rows for the new provider without touching existing data.