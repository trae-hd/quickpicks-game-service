-- V021__align_outbox_events.sql
ALTER TABLE outbox_events RENAME TO event_outbox;

ALTER TABLE event_outbox RENAME COLUMN processed_at TO sent_at;
ALTER TABLE event_outbox ADD COLUMN player_id VARCHAR(100);
ALTER TABLE event_outbox ADD COLUMN batch_eligible BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE event_outbox ADD COLUMN event_version INT NOT NULL DEFAULT 1;

-- Optimized index for SKIP LOCKED processing
DROP INDEX IF EXISTS idx_outbox_unprocessed;
CREATE INDEX idx_outbox_unsent ON event_outbox (created_at ASC) WHERE sent_at IS NULL;
