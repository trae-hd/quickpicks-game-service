ALTER TABLE player_promotions
  ADD COLUMN promotion_type TEXT NOT NULL DEFAULT 'FIRST_ENTRY_FREE',
  ADD COLUMN awarded_by     TEXT,
  ADD COLUMN award_reason   TEXT,
  ADD COLUMN campaign_id    TEXT;

-- Backfill: existing rows are all acquisition free entries
UPDATE player_promotions SET promotion_type = 'FIRST_ENTRY_FREE' WHERE promotion_type IS NULL;
