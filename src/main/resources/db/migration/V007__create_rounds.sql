CREATE TABLE rounds (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    slate_id            UUID NOT NULL REFERENCES slates(id),
    tenant_id           TEXT NOT NULL REFERENCES tenants(id),
    jackpot_pool_pence  BIGINT NOT NULL DEFAULT 0,
    eleven_pool_pence   BIGINT NOT NULL DEFAULT 0,
    ten_pool_pence      BIGINT NOT NULL DEFAULT 0,
    rollover_in_pence   BIGINT NOT NULL DEFAULT 0,
    remainder_pence     BIGINT NOT NULL DEFAULT 0,
    required_not_flags  JSONB NOT NULL DEFAULT '[]',
    status              TEXT NOT NULL DEFAULT 'OPEN',
    locked_at           TIMESTAMPTZ,
    full_time_at        TIMESTAMPTZ,
    settle_after        TIMESTAMPTZ,
    settled_at          TIMESTAMPTZ,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);

ALTER TABLE rounds ENABLE ROW LEVEL SECURITY;
CREATE POLICY rounds_tenant_isolation ON rounds
  FOR ALL
  USING (tenant_id = current_setting('app.current_tenant', true))
  WITH CHECK (tenant_id = current_setting('app.current_tenant', true));
