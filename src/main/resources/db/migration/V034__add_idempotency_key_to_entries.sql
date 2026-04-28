ALTER TABLE entries
    ADD COLUMN IF NOT EXISTS idempotency_key TEXT;

CREATE UNIQUE INDEX IF NOT EXISTS uq_entries_idempotency_key
    ON entries(tenant_id, idempotency_key)
    WHERE idempotency_key IS NOT NULL;
