-- Creates qplay_app role for application connections.
-- The app uses SET ROLE qplay_app after connecting as the migration user (postgres).
-- Non-superuser roles are subject to RLS policies; superusers (postgres) bypass them.
DO
$$
BEGIN
    IF NOT EXISTS (SELECT FROM pg_catalog.pg_roles WHERE rolname = 'qplay_app') THEN

        CREATE ROLE qplay_app;
    END IF;
END
$$;

GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO qplay_app;
GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA public TO qplay_app;

-- Ensure future tables and sequences created by the migration user also grant to qplay_app
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO qplay_app;
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT USAGE, SELECT ON SEQUENCES TO qplay_app;