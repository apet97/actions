-- H6 + M12: rename the idempotency constraint to the final canonical name
ALTER TABLE webhook_events DROP CONSTRAINT IF EXISTS uq_webhook_event;
ALTER TABLE webhook_events ADD CONSTRAINT uq_webhook_events_workspace_event
    UNIQUE (workspace_id, event_id);
