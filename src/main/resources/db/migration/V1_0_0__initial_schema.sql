-- Installations
CREATE TABLE installations (
    workspace_id    VARCHAR(64) PRIMARY KEY,
    addon_id        VARCHAR(64) NOT NULL,
    auth_token_enc  TEXT NOT NULL,
    api_url         VARCHAR(512) NOT NULL,
    addon_user_id   VARCHAR(64),
    status          VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    installed_at    TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP NOT NULL DEFAULT NOW()
);

-- Per-webhook auth tokens
CREATE TABLE webhook_tokens (
    id              BIGSERIAL PRIMARY KEY,
    workspace_id    VARCHAR(64) NOT NULL REFERENCES installations(workspace_id) ON DELETE CASCADE,
    webhook_path    VARCHAR(256) NOT NULL,
    auth_token_enc  TEXT NOT NULL,
    UNIQUE(workspace_id, webhook_path)
);

-- Actions (the core entity)
CREATE TABLE actions (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    workspace_id    VARCHAR(64) NOT NULL REFERENCES installations(workspace_id) ON DELETE CASCADE,
    name            VARCHAR(128) NOT NULL,
    event_type      VARCHAR(64) NOT NULL,
    enabled         BOOLEAN NOT NULL DEFAULT TRUE,
    http_method     VARCHAR(8) NOT NULL,
    url_template    TEXT NOT NULL,
    headers_enc     TEXT,
    body_template   TEXT,
    signing_secret_enc TEXT,
    retry_count     INT NOT NULL DEFAULT 3,
    created_at      TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_actions_workspace_event ON actions(workspace_id, event_type) WHERE enabled = TRUE;

-- Execution logs
CREATE TABLE execution_logs (
    id              BIGSERIAL PRIMARY KEY,
    action_id       UUID NOT NULL REFERENCES actions(id) ON DELETE CASCADE,
    workspace_id    VARCHAR(64) NOT NULL,
    event_type      VARCHAR(64) NOT NULL,
    request_url     TEXT,
    request_method  VARCHAR(8),
    response_status INT,
    response_time_ms INT,
    response_body   TEXT,
    error_message   TEXT,
    success         BOOLEAN NOT NULL,
    executed_at     TIMESTAMP NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_logs_action_time ON execution_logs(action_id, executed_at DESC);
CREATE INDEX idx_logs_workspace_time ON execution_logs(workspace_id, executed_at DESC);
