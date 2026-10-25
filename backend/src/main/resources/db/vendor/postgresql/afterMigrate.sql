-- Runs after every Flyway migrate (PostgreSQL only), so tables added by
-- future migrations are covered automatically. Safe to run repeatedly.
--
-- Supabase exposes the public schema through its Data API, and its default
-- grants give the anon and authenticated roles full access to new tables.
-- Clerova reads and writes data only through its own backend, which connects
-- as the table owner (owners are not subject to RLS). Close the Data API path:
--   * enable row level security, with no policies, on every table, and
--   * revoke the Data API roles' privileges, now and for future tables.

DO $$
DECLARE
    table_record RECORD;
    has_api_roles BOOLEAN :=
        EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'anon')
        AND EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'authenticated');
BEGIN
    FOR table_record IN
        SELECT tablename FROM pg_tables WHERE schemaname = current_schema()
    LOOP
        EXECUTE format('ALTER TABLE %I ENABLE ROW LEVEL SECURITY', table_record.tablename);

        IF has_api_roles THEN
            EXECUTE format('REVOKE ALL ON TABLE %I FROM anon, authenticated', table_record.tablename);
        END IF;
    END LOOP;

    IF has_api_roles THEN
        EXECUTE 'REVOKE ALL ON ALL SEQUENCES IN SCHEMA ' || quote_ident(current_schema()) || ' FROM anon, authenticated';
        EXECUTE 'ALTER DEFAULT PRIVILEGES IN SCHEMA ' || quote_ident(current_schema()) || ' REVOKE ALL ON TABLES FROM anon, authenticated';
        EXECUTE 'ALTER DEFAULT PRIVILEGES IN SCHEMA ' || quote_ident(current_schema()) || ' REVOKE ALL ON SEQUENCES FROM anon, authenticated';
    END IF;
END
$$;
