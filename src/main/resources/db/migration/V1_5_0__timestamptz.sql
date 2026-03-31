-- M13: Convert all TIMESTAMP columns to TIMESTAMPTZ for proper timezone handling
ALTER TABLE installations ALTER COLUMN installed_at TYPE TIMESTAMPTZ;
ALTER TABLE installations ALTER COLUMN updated_at TYPE TIMESTAMPTZ;
ALTER TABLE actions ALTER COLUMN created_at TYPE TIMESTAMPTZ;
ALTER TABLE actions ALTER COLUMN updated_at TYPE TIMESTAMPTZ;
ALTER TABLE actions ALTER COLUMN last_scheduled_run TYPE TIMESTAMPTZ;
ALTER TABLE execution_logs ALTER COLUMN executed_at TYPE TIMESTAMPTZ;
ALTER TABLE webhook_events ALTER COLUMN received_at TYPE TIMESTAMPTZ;
