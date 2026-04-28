


CREATE TABLE tenants (
    id                          TEXT PRIMARY KEY,
    name                        TEXT NOT NULL,
    jwt_secret                  TEXT NOT NULL,
    wallet_base_url             TEXT NOT NULL,
    wallet_hmac_secret          TEXT NOT NULL,
    allowed_host_origins        JSONB NOT NULL DEFAULT '[]',
    currency                    TEXT NOT NULL DEFAULT 'GBP',
    optimove_api_key_encrypted  TEXT,
    optimove_stream_id          TEXT,
    feature_flags               JSONB NOT NULL DEFAULT '{}',
    targeting_mode              TEXT NOT NULL DEFAULT 'OPEN',
    free_entry_enabled          BOOLEAN NOT NULL DEFAULT false,
    quick_picks_launched_at     TIMESTAMPTZ,
    free_entry_grace_minutes    INT NOT NULL DEFAULT 120,
    configured_leagues          JSONB NOT NULL DEFAULT '[]',
    slate_window_hours          INT NOT NULL DEFAULT 48,
    primary_timezone            TEXT NOT NULL DEFAULT 'Europe/London',
    dominant_style_threshold_pct INT NOT NULL DEFAULT 60,
    created_at                  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at                  TIMESTAMPTZ NOT NULL DEFAULT now()
);
