-- Local development tenant. Idempotent: safe to run in any environment.
-- CORS is open (*) — restrict allowed_host_origins before deploying to staging/prod.
INSERT INTO tenants (
    id,
    name,
    jwt_secret,
    wallet_base_url,
    wallet_hmac_secret,
    allowed_host_origins,
    currency,
    free_entry_enabled,
    targeting_mode,
    primary_timezone,
    slate_window_hours
) VALUES (
    'mrq',
    'MRQ (Dev)',
    'dev-mrq-player-jwt-secret-local-only-min32chars',
    'http://localhost:9090',
    'dev-mrq-wallet-hmac-secret-local-only',
    '["*"]',
    'GBP',
    true,
    'OPEN',
    'Europe/London',
    48
)
ON CONFLICT (id) DO NOTHING;
