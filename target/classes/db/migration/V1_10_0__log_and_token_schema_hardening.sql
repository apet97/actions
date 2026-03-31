CREATE INDEX IF NOT EXISTS idx_logs_executed_at ON execution_logs(executed_at);

ALTER TABLE webhook_tokens
    ADD COLUMN IF NOT EXISTS created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    ADD COLUMN IF NOT EXISTS updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW();
