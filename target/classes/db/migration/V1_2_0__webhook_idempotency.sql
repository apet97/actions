CREATE TABLE IF NOT EXISTS webhook_events (
    id BIGSERIAL PRIMARY KEY,
    workspace_id VARCHAR(64) NOT NULL,
    event_id VARCHAR(128) NOT NULL,
    event_type VARCHAR(64) NOT NULL,
    received_at TIMESTAMP DEFAULT NOW(),
    CONSTRAINT uq_webhook_event UNIQUE (workspace_id, event_id)
);
CREATE INDEX idx_webhook_events_cleanup ON webhook_events (received_at);
