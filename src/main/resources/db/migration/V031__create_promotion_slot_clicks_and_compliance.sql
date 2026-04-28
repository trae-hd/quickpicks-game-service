CREATE TABLE promotion_slot_clicks (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    slot_id         UUID NOT NULL REFERENCES tenant_promotion_slots(id),
    tenant_id       TEXT NOT NULL REFERENCES tenants(id),
    round_id        UUID,
    host_player_id  TEXT NOT NULL,
    clicked_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_promo_clicks_slot_round ON promotion_slot_clicks(slot_id, round_id);

ALTER TABLE promotion_slot_clicks ENABLE ROW LEVEL SECURITY;
CREATE POLICY promotion_slot_clicks_tenant_isolation ON promotion_slot_clicks
  FOR ALL
  USING (tenant_id = current_setting('app.current_tenant', true))
  WITH CHECK (tenant_id = current_setting('app.current_tenant', true));

ALTER TABLE tenants
  ADD COLUMN cross_sell_compliance_mode TEXT NOT NULL DEFAULT 'PERMISSIVE';
  -- Values: 'UK_STRICT' (SPORTS/OTHER only), 'PERMISSIVE' (all categories), 'DISABLED' (no slots rendered)
