-- Add action_type, operation_id, and parameters_enc columns for Clockify Actions support
ALTER TABLE actions ADD COLUMN action_type VARCHAR(16) NOT NULL DEFAULT 'CUSTOM';
ALTER TABLE actions ADD COLUMN operation_id VARCHAR(64);
ALTER TABLE actions ADD COLUMN parameters_enc TEXT;

-- Make http_method, url_template nullable for CLOCKIFY actions (computed at execution time)
ALTER TABLE actions ALTER COLUMN http_method DROP NOT NULL;
ALTER TABLE actions ALTER COLUMN url_template DROP NOT NULL;
