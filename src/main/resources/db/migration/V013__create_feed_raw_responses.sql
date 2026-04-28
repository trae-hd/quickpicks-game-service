-- V013__create_feed_raw_responses.sql
CREATE TABLE feed_raw_responses (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    provider_id TEXT NOT NULL REFERENCES feed_providers(id),
    endpoint TEXT NOT NULL,
    response_body JSONB NOT NULL,
    fetched_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- feed_raw_responses is for debugging/audit and is global to the service.
