CREATE TABLE feed_providers (
    id                          TEXT PRIMARY KEY,
    name                        TEXT NOT NULL,
    base_url                    TEXT NOT NULL,
    polling_intervals_json      JSONB NOT NULL DEFAULT '{}',
    created_at                  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at                  TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- feed_providers is NOT tenant-scoped.
