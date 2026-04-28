CREATE TABLE tenant_promotion_slots (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id         TEXT NOT NULL REFERENCES tenants(id),
    slot_position     TEXT NOT NULL,
    product_category  TEXT NOT NULL DEFAULT 'OTHER',
    title             TEXT NOT NULL,
    subtitle          TEXT,
    image_url         TEXT,
    cta_text          TEXT NOT NULL,
    cta_action        TEXT NOT NULL DEFAULT 'NAVIGATE',
    cta_url           TEXT NOT NULL,
    is_active         BOOLEAN NOT NULL DEFAULT true,
    priority          INT NOT NULL DEFAULT 0,
    start_at          TIMESTAMPTZ,
    end_at            TIMESTAMPTZ,
    click_count       BIGINT NOT NULL DEFAULT 0,
    impression_count  BIGINT NOT NULL DEFAULT 0,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT valid_slot_position CHECK (slot_position IN ('POST_ENTRY_RECEIPT', 'HUB_BANNER')),
    CONSTRAINT valid_product_category CHECK (product_category IN ('SPORTS', 'CASINO', 'GAMING', 'OTHER')),
    CONSTRAINT valid_cta_action CHECK (cta_action IN ('NAVIGATE'))
);

ALTER TABLE tenant_promotion_slots ENABLE ROW LEVEL SECURITY;
CREATE POLICY tenant_promotion_slots_tenant_isolation ON tenant_promotion_slots
  FOR ALL
  USING (tenant_id = current_setting('app.current_tenant', true))
  WITH CHECK (tenant_id = current_setting('app.current_tenant', true));
