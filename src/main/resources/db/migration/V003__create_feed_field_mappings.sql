CREATE TABLE feed_field_mappings (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    provider_id         TEXT NOT NULL REFERENCES feed_providers(id),
    canonical_field     TEXT NOT NULL,
    provider_json_path  TEXT NOT NULL,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE(provider_id, canonical_field)
);

-- feed_field_mappings is NOT tenant-scoped.
