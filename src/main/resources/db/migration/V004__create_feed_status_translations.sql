CREATE TABLE feed_status_translations (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    provider_id         TEXT NOT NULL REFERENCES feed_providers(id),
    provider_status     TEXT NOT NULL,
    canonical_status    TEXT NOT NULL,
    alert_severity      TEXT NOT NULL DEFAULT 'NONE',
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE(provider_id, provider_status)
);

-- feed_status_translations is NOT tenant-scoped.
