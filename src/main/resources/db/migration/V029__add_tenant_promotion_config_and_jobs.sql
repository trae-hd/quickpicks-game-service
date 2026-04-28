ALTER TABLE tenants
  ADD COLUMN max_active_free_entries_per_player INT NOT NULL DEFAULT 3;

CREATE TABLE promotion_award_jobs (
  id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id        TEXT NOT NULL REFERENCES tenants(id),
  campaign_id      TEXT,
  total_requested  INT NOT NULL,
  total_processed  INT NOT NULL DEFAULT 0,
  total_awarded    INT NOT NULL DEFAULT 0,
  total_skipped    INT NOT NULL DEFAULT 0,
  total_failed     INT NOT NULL DEFAULT 0,
  status           TEXT NOT NULL DEFAULT 'PROCESSING',   -- PROCESSING, COMPLETED, FAILED
  created_by       TEXT NOT NULL,
  created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
  completed_at     TIMESTAMPTZ
);

ALTER TABLE promotion_award_jobs ENABLE ROW LEVEL SECURITY;
CREATE POLICY promotion_award_jobs_tenant_isolation ON promotion_award_jobs
  FOR ALL
  USING (tenant_id = current_setting('app.current_tenant', true))
  WITH CHECK (tenant_id = current_setting('app.current_tenant', true));
