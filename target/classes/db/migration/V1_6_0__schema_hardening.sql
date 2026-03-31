ALTER TABLE webhook_events
    ALTER COLUMN event_id TYPE VARCHAR(256),
    ALTER COLUMN received_at SET NOT NULL;

ALTER TABLE actions
    ALTER COLUMN success_conditions TYPE JSONB USING success_conditions::jsonb,
    ALTER COLUMN conditions TYPE JSONB USING conditions::jsonb;

ALTER TABLE actions
    ADD CONSTRAINT chk_chain_order CHECK (chain_order IS NULL OR (chain_order >= 1 AND chain_order <= 100));
