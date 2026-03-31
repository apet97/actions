-- P1/P2 feature columns for actions table

-- F13: Response conditions (JSON array of {field, operator, value})
ALTER TABLE actions ADD COLUMN success_conditions TEXT;

-- F17: Chained actions (execution order within same event, NULL = independent)
ALTER TABLE actions ADD COLUMN chain_order INT;

-- F18: Conditional execution (JSON array of {field, operator, value})
ALTER TABLE actions ADD COLUMN conditions TEXT;

-- F19: Scheduled actions (cron expression, NULL = webhook-triggered only)
ALTER TABLE actions ADD COLUMN cron_expression VARCHAR(64);
ALTER TABLE actions ADD COLUMN last_scheduled_run TIMESTAMP;

-- Widget stats: aggregate query index
CREATE INDEX idx_logs_workspace_success ON execution_logs(workspace_id, success, executed_at DESC);
