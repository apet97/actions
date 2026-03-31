# Clockify HTTP Actions — Technical Specification

**Version:** 1.1
**Date:** 2026-03-31

---

## 1. System Architecture

```
                        Clockify Platform
                    ┌──────────┬──────────┐
                    │Lifecycle │ Webhooks  │  Iframe
                    │ Events   │ (signed)  │  Loading
                    └────┬─────┴────┬──────┘────┬────
                         │          │           │
                    HTTPS (TLS 1.2+, ngrok dev / Caddy prod)
                         │          │           │
┌────────────────────────▼──────────▼───────────▼────────────────┐
│                 Spring Boot 3.5.13 / Java 21                    │
│                                                                 │
│  ┌──────────────┐  ┌──────────────┐  ┌────────────────────────┐ │
│  │ Lifecycle     │  │ Webhook      │  │ Sidebar / Widget / API │ │
│  │ Controller    │  │ Controller   │  │ Controllers            │ │
│  │ /lifecycle/*  │  │ /webhook/*   │  │ /sidebar /widget /api  │ │
│  └──────┬───────┘  └──────┬───────┘  └────────────┬───────────┘ │
│         │                  │                       │             │
│  ┌──────▼──────────────────▼───────────────────────▼───────────┐│
│  │                    Filters + Service Layer                   ││
│  │  RequestCorrelationFilter  AddonClaimsFilter               ││
│  │  RequestSizeLimitFilter    RateLimitFilter                 ││
│  │  InstallationService  WebhookRequestService                ││
│  │  ActionService  ExecutionService  HttpActionExecutor       ││
│  │  TokenService  VariableInterpolator  LogService            ││
│  └──────┬──────────────────┬───────────────────────┬───────────┘│
│         │                  │                       │             │
│  ┌──────▼────────────┐  ┌───────────▼────────────────────────┐│
│  │ PostgreSQL        │  │ RestClient + Apache HttpClient 5  ││
│  │ (encrypted) via   │  │ (pinned DNS, outbound HTTP)       ││
│  │ JPA + Flyway      │  │                                    ││
│  └───────────────────┘  └────────────────────────────────────┘│
│                                                                 │
│  Observability: SLF4J + Micrometer + Actuator                  │
└─────────────────────────────────────────────────────────────────┘
         │                                    │
         │ Outbound HTTP Actions              │
         ▼                                    ▼
   ┌───────────┐                    ┌───────────────┐
   │ User's    │                    │ User's        │
   │ API #1    │                    │ API #2        │
   └───────────┘                    └───────────────┘
```

## 2. Manifest Definition

```json
{
  "schemaVersion": "1.3",
  "key": "clockify-http-actions",
  "name": "HTTP Actions",
  "baseUrl": "${ADDON_BASE_URL}",
  "description": "Turn Clockify events into HTTP requests. Postman meets webhooks.",
  "iconPath": "/icon.png",
  "minimalSubscriptionPlan": "FREE",
  "scopes": [
    "TIME_ENTRY_READ", "TIME_ENTRY_WRITE",
    "PROJECT_READ", "PROJECT_WRITE",
    "TASK_READ", "TASK_WRITE",
    "CLIENT_READ", "CLIENT_WRITE",
    "TAG_READ", "TAG_WRITE",
    "USER_READ", "GROUP_WRITE",
    "INVOICE_READ", "INVOICE_WRITE",
    "CUSTOM_FIELDS_READ", "CUSTOM_FIELDS_WRITE"
  ],
  "lifecycle": [
    { "path": "/lifecycle/installed", "type": "INSTALLED" },
    { "path": "/lifecycle/deleted", "type": "DELETED" },
    { "path": "/lifecycle/status-changed", "type": "STATUS_CHANGED" },
    { "path": "/lifecycle/settings-updated", "type": "SETTINGS_UPDATED" }
  ],
  "webhooks": [
    { "event": "NEW_TIME_ENTRY", "path": "/webhook/new-time-entry" },
    { "event": "TIME_ENTRY_UPDATED", "path": "/webhook/time-entry-updated" },
    { "event": "NEW_TIMER_STARTED", "path": "/webhook/new-timer-started" },
    { "event": "TIMER_STOPPED", "path": "/webhook/timer-stopped" },
    { "event": "NEW_PROJECT", "path": "/webhook/new-project" },
    { "event": "NEW_CLIENT", "path": "/webhook/new-client" },
    { "event": "NEW_INVOICE", "path": "/webhook/new-invoice" },
    { "event": "USER_JOINED_WORKSPACE", "path": "/webhook/user-joined" },
    { "event": "NEW_TASK", "path": "/webhook/new-task" },
    { "event": "TIME_ENTRY_DELETED", "path": "/webhook/time-entry-deleted" }
  ],
  "components": [
    {
      "type": "sidebar",
      "label": "HTTP Actions",
      "path": "/sidebar",
      "accessLevel": "ADMINS",
      "iconPath": "/sidebar-icon.png"
    },
    {
      "type": "widget",
      "label": "HTTP Actions",
      "path": "/widget",
      "accessLevel": "ADMINS"
    }
  ]
}
```

**Design decision: Fixed 10 webhooks.** The manifest registers the 10 supported events at install time. Internally, the addon only forwards events that have active actions. Events without actions are received and discarded. This keeps the manifest stable and avoids re-install churn when users edit actions.

`USER_JOINED_WORKSPACE` uses the addon-defined path `/webhook/user-joined`. That path must stay aligned with `EventType.USER_JOINED_WORKSPACE.getSlug()` and the manifest/controller tests.

## 3. Endpoint Map

| Method | Path | Purpose | Auth |
|--------|------|---------|------|
| GET | `/manifest` | Serve manifest JSON (empty array stripping) | None |
| GET | `/sidebar` | Sidebar iframe UI (Thymeleaf) | User token in `?auth_token=` |
| POST | `/lifecycle/installed` | Store installation + webhook tokens | `X-Addon-Lifecycle-Token` |
| POST | `/lifecycle/deleted` | Delete all workspace data | `X-Addon-Lifecycle-Token` |
| POST | `/lifecycle/status-changed` | Pause/resume processing for webhook and scheduled execution | `X-Addon-Lifecycle-Token` |
| POST | `/lifecycle/settings-updated` | Sync settings | `X-Addon-Lifecycle-Token` |
| POST | `/webhook/{event-slug}` | Receive webhook, match actions, execute | `Clockify-Signature` |
| GET | `/api/actions` | List actions for workspace | `X-Addon-Token` (user) |
| POST | `/api/actions` | Create new action (`201 Created`) | `X-Addon-Token` (user) |
| PUT | `/api/actions/{id}` | Update action | `X-Addon-Token` (user) |
| DELETE | `/api/actions/{id}` | Delete action (`204 No Content`) | `X-Addon-Token` (user) |
| POST | `/api/actions/{id}/test` | Test-fire action with sample data | `X-Addon-Token` (user) |
| GET | `/api/actions/{id}/logs` | Get execution logs for action | `X-Addon-Token` (user) |
| GET | `/api/events` | List available event types + sample payloads | `X-Addon-Token` (user) |
| GET | `/api/logs` | Workspace-wide execution logs | `X-Addon-Token` (user) |
| GET | `/api/actions/export` | Export all actions (JSON, secrets redacted) | `X-Addon-Token` (user) |
| POST | `/api/actions/import` | Import actions from JSON array | `X-Addon-Token` (user) |
| GET | `/api/templates` | Pre-built action templates | `X-Addon-Token` (user) |
| GET | `/api/widget/stats` | Widget JSON stats refresh endpoint | `X-Addon-Token` (user) |
| GET | `/api/health` | Health check (DB connectivity) | None |
| GET | `/widget` | Widget dashboard (stats) | User token in `?auth_token=` |
| GET | `/static/**` | CSS, JS, icons | None |

## 4. Data Model

### ERD

```
installations ──1:N── actions ──1:N── execution_logs
     │
     └──1:N── webhook_tokens
```

### Tables

**`installations`**
```sql
CREATE TABLE installations (
    workspace_id    VARCHAR(64) PRIMARY KEY,
    addon_id        VARCHAR(64) NOT NULL,
    auth_token_enc  TEXT NOT NULL,           -- AES-256 encrypted
    api_url         VARCHAR(512) NOT NULL,
    addon_user_id   VARCHAR(64),
    status          VARCHAR(16) DEFAULT 'ACTIVE',  -- ACTIVE / INACTIVE
    installed_at    TIMESTAMPTZ DEFAULT NOW(),
    updated_at      TIMESTAMPTZ DEFAULT NOW()
);
```

**`webhook_tokens`**
```sql
CREATE TABLE webhook_tokens (
    id              BIGSERIAL PRIMARY KEY,
    workspace_id    VARCHAR(64) REFERENCES installations(workspace_id) ON DELETE CASCADE,
    webhook_path    VARCHAR(256) NOT NULL,
    auth_token_enc  TEXT NOT NULL,           -- AES-256 encrypted
    created_at      TIMESTAMPTZ DEFAULT NOW(),
    updated_at      TIMESTAMPTZ DEFAULT NOW(),
    UNIQUE(workspace_id, webhook_path)
);
```

**`actions`**
```sql
CREATE TABLE actions (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    workspace_id    VARCHAR(64) REFERENCES installations(workspace_id) ON DELETE CASCADE,
    name            VARCHAR(128) NOT NULL,
    event_type      VARCHAR(64) NOT NULL,    -- e.g. NEW_TIME_ENTRY
    enabled         BOOLEAN DEFAULT TRUE,
    http_method     VARCHAR(8) NOT NULL,     -- GET/POST/PUT/PATCH/DELETE
    url_template    TEXT NOT NULL,            -- supports {{variables}}
    headers_enc     TEXT,                     -- JSON object, AES-256 encrypted
    body_template   TEXT,                     -- supports {{variables}}
    signing_secret_enc TEXT,                  -- HMAC secret, AES-256 encrypted, nullable
    retry_count     INT DEFAULT 3 CHECK (retry_count >= 0 AND retry_count <= 10),
    success_conditions JSONB,                 -- typed success-condition list
    chain_order     INT,                      -- sequential execution order
    conditions      JSONB,                    -- typed execution-condition list
    cron_expression VARCHAR(64),              -- Spring cron format
    last_scheduled_run TIMESTAMPTZ,
    created_at      TIMESTAMPTZ DEFAULT NOW(),
    updated_at      TIMESTAMPTZ DEFAULT NOW()
);
CREATE INDEX idx_actions_workspace_event ON actions(workspace_id, event_type);
```

**`execution_logs`**
```sql
CREATE TABLE execution_logs (
    id              BIGSERIAL PRIMARY KEY,
    action_id       UUID REFERENCES actions(id) ON DELETE CASCADE,
    workspace_id    VARCHAR(64) NOT NULL,
    event_type      VARCHAR(64) NOT NULL,
    request_url     TEXT,
    request_method  VARCHAR(8),
    response_status INT,
    response_time_ms INT,
    response_body   TEXT,                    -- truncated to 4KB
    error_message   TEXT,
    success         BOOLEAN NOT NULL,
    executed_at     TIMESTAMPTZ DEFAULT NOW()
);
CREATE INDEX idx_logs_action_time ON execution_logs(action_id, executed_at DESC);
CREATE INDEX idx_logs_executed_at ON execution_logs(executed_at);
-- Auto-cleanup: keep 30 days (CleanupService @Scheduled)
```

**`webhook_events`** (V1_2_0 — idempotency deduplication)
```sql
CREATE TABLE webhook_events (
    id              BIGSERIAL PRIMARY KEY,
    workspace_id    VARCHAR(64) NOT NULL,
    event_id        VARCHAR(256) NOT NULL,
    event_type      VARCHAR(64) NOT NULL,
    received_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_webhook_events_workspace_event UNIQUE (workspace_id, event_id)
);
-- Auto-cleanup: keep 7 days (CleanupService @Scheduled)
```

## 5. Webhook Processing Pipeline

```
1. Clockify sends POST /webhook/{event-slug}
      │
2. Enforce 1 MB request-size limit (`RequestSizeLimitFilter`) on `/api/*`, `/webhook/*`, and `/lifecycle/*`
      │ fail → 413
      │
3. Verify Clockify-Signature JWT (RSA256)
      │ fail → 401
      │
4. Extract workspaceId + addonId from claims
      │
5. Check installation exists + status = ACTIVE
      │ fail → 200 (accept but discard)
      │
6. Extract event type from `clockify-webhook-event-type` header
      │
7. Verify stored per-webhook auth token + insert idempotency row keyed by `(workspaceId, SHA-256(eventType + ":" + rawPayload))`
      │ duplicate → 200
      │
8. Return 200 OK quickly; async work continues after commit
      │
9. [ASYNC] Find all enabled actions for (workspaceId, eventType)
      │ none → discard, log metric
      │
10. For each matching action:
      │
   8a. Parse webhook payload JSON
   8b. Interpolate template variables in URL, headers, body
   8c. Execute HTTP request via RestClient + Apache HttpClient 5
   8d. Log result to execution_logs
   8e. On failure: retry with backoff (1s, 5s, then capped 25s) up to retry_count, bounded by a 60-second retry window
   8f. On final failure: log as failed, increment failure metric
```

`GET /widget` renders the initial iframe HTML only. After the first load, the frontend removes `auth_token` from the URL, then polls `GET /api/widget/stats` with `X-Addon-Token` for refreshes. The sidebar follows the same one-time token extraction pattern and requests a refreshed addon token both on a 25-minute cadence and once on `401`.

## 6. Template Engine

### Variable Syntax

```
{{event.userId}}                  -- top-level payload field
{{event.timeInterval.start}}      -- nested field (dot-notation)
{{event.tags[0].name}}            -- array access
{{event.raw}}                     -- entire payload as raw JSON string
{{meta.workspaceId}}              -- workspace ID from JWT claims
{{meta.eventType}}                -- event type string
{{meta.receivedAt}}               -- ISO 8601 timestamp of webhook receipt
{{meta.addonId}}                  -- addon installation ID
{{meta.backendUrl}}               -- Clockify API base URL (from installation)
{{meta.installationToken}}        -- installation token for Clockify API calls
```

### Interpolation Rules

1. Parse template string for `{{...}}` patterns
2. For each variable, traverse the JSON payload using dot-notation
3. If field not found → empty string (not error)
4. If field is an object/array → serialize to JSON string
5. `{{event.raw}}` is special: outputs the entire webhook payload as JSON (for passthrough)
6. Variables in URL query string are URL-encoded after interpolation; path variables are not encoded
7. Variables in headers have `\r` and `\n` stripped after interpolation (CRLF injection defense)
8. Variables in body are used as-is (user controls encoding)

### Implementation

```java
public class VariableInterpolator {
    private static final Pattern VARIABLE_PATTERN = Pattern.compile("\\{\\{(.+?)\\}\\}");

    public String interpolate(String template, JsonObject event, Map<String, String> meta) {
        return VARIABLE_PATTERN.matcher(template).replaceAll(match -> {
            String path = match.group(1);
            if (path.startsWith("meta.")) return meta.getOrDefault(path.substring(5), "");
            if (path.equals("event.raw")) return event.toString();
            return resolveJsonPath(event, path.substring(6)); // strip "event."
        });
    }

    private String resolveJsonPath(JsonObject obj, String dotPath) {
        // Walk dot-notation path, handle arrays with [n], return "" on miss
    }
}
```

## 7. Security Model

### Inbound (Clockify → Addon)

| Request Type | Verification |
|-------------|-------------|
| Lifecycle | Verify `X-Addon-Lifecycle-Token` JWT: RSA256, `iss=clockify`, `type=addon`, `sub=addon-key`; cross-check claim/body workspace IDs |
| Webhook | Verify `Clockify-Signature` JWT, validate stored per-webhook token, fingerprint the exact payload (`eventType + ":" + rawPayload`), and dedupe via `webhook_events` |
| Sidebar / Widget UI | User token in `?auth_token=` query param. `IframeViewSupport` verifies the JWT server-side and passes workspace/language/timezone/theme into the model. |
| Internal API (`/api/*`) | `AddonClaimsFilter` parses `X-Addon-Token`, stores a `VerifiedAddonContext`, and MVC resolves it directly into controller arguments |

### Outbound (Addon → User's API)

| Feature | Implementation |
|---------|---------------|
| HMAC signing (optional) | `X-HTTP-Actions-Signature` header with HMAC-SHA256 of request body using per-action signing secret |
| TLS | RestClient enforces HTTPS for outbound URLs. Allow HTTP only in dev profile. |
| Timeout | 10-second connect timeout, 30-second read timeout per outbound request |
| Size limit | Incoming request body max 1MB on `/api/*`, `/webhook/*`, and `/lifecycle/*`. Expanded URL max 8KB. Template-expanded request body max 1MB. Response body truncated to 4KB in logs. |
| Correlation | `RequestCorrelationFilter` sets or propagates `X-Request-Id` and mirrors it back in the response |

### At Rest

| Data | Encryption |
|------|-----------|
| Installation auth tokens | AES-256 (Spring Security Crypto) |
| Webhook auth tokens | AES-256 |
| Action headers (may contain API keys) | AES-256 |
| Action signing secrets | AES-256 |
| Execution logs (response bodies) | Plaintext (no secrets expected in responses) |

### Input Validation

| Field | Validation |
|-------|-----------|
| Action URL | Must be valid URL. Must be HTTPS in production. Max 2048 chars. |
| Action name | 1-128 chars, alphanumeric + spaces + dashes (compiled `Pattern`) |
| Headers | Max 20 headers. Key: 1-128 chars. Value: max 4096 chars. CRLF stripped from interpolated values. |
| Body template | Max 64KB |
| Event type | Must be one of the 10 registered webhook events |
| Execution condition operators | Must be one of: `equals`, `not_equals`, `contains`, `gt`, `lt`, `exists`, `not_exists` |
| Success condition operators | Must be one of: `status_range`, `body_contains`, `body_not_contains` |
| Encryption key | 64+ hex chars (format validated at startup via regex) |
| Encryption salt | 32+ hex chars (format validated at startup via regex) |

## 8. Concurrency & Multi-Tenancy

- All queries include `WHERE workspace_id = ?` — no cross-tenant data access
- API rate limit: **10 requests/second per workspace per group** (`actions`, `logs`, `events`, `templates`, `widget-stats`)
- Unauthenticated `/api/*` requests fall back to IP-based throttling at one-third of the workspace limit
- `/api/health` bypasses addon-auth parsing and rate limiting so health probes cannot throttle themselves
- Action execution is async (virtual threads): webhook handler returns 200, processing happens in background
- Database connection pool: HikariCP defaults (10 connections), sufficient for multi-workspace load
- Caffeine local cache for event schemas and workspace lookups (5m TTL, 1000 max entries)
- Caffeine is used for lightweight in-memory rate-limit buckets

## 9. Sidebar UI

### Layout

```
┌─ HTTP Actions ─────────────────────────────────────┐
│                                                     │
│  [+ New Action]                         [Actions ▼] │
│                                                     │
│  ┌─ Action: "Billing Sync" ──────────────────────┐ │
│  │                                                │ │
│  │  Trigger:  [NEW_TIME_ENTRY           ▼]       │ │
│  │                                                │ │
│  │  Method:   [POST ▼]                           │ │
│  │  URL:      [https://billing.co/api/entries   ]│ │
│  │                                                │ │
│  │  Headers:                                      │ │
│  │  ┌────────────────┬───────────────────────┐   │ │
│  │  │ Authorization   │ Bearer sk-xxx         │   │ │
│  │  │ Content-Type    │ application/json      │   │ │
│  │  │ [+ Add header]                          │   │ │
│  │  └────────────────┴───────────────────────┘   │ │
│  │                                                │ │
│  │  Body:                                         │ │
│  │  ┌────────────────────────────────────────┐   │ │
│  │  │ {                                       │   │ │
│  │  │   "user": "{{event.userId}}",           │   │ │
│  │  │   "project": "{{event.projectId}}",     │   │ │
│  │  │   "hours": "{{event.timeInterval.dur}}",│   │ │
│  │  │   "desc": "{{event.description}}"       │   │ │
│  │  │ }                                       │   │ │
│  │  └────────────────────────────────────────┘   │ │
│  │                                                │ │
│  │  [▶ Test]    [ON/OFF]    [Save]    [Delete]   │ │
│  └────────────────────────────────────────────────┘ │
│                                                     │
│  ── Recent Executions ─────────────────────────────│
│  ┌────────┬──────────────────┬────┬──────┐        │
│  │ 12:04  │ NEW_TIME_ENTRY   │ 200│ 142ms│        │
│  │ 12:01  │ NEW_TIME_ENTRY   │ 200│ 98ms │        │
│  │ 11:58  │ TIMER_STOPPED    │ 401│ 55ms │  ← red │
│  │ 11:30  │ NEW_TIME_ENTRY   │ 200│ 201ms│        │
│  └────────┴──────────────────┴────┴──────┘        │
│  [View all logs →]                                  │
│                                                     │
└─────────────────────────────────────────────────────┘
```

### UI Technology

- **Server:** Thymeleaf templates rendered by Spring Boot
- **Styling:** Clockify UI Kit CSS (`resources.developer.clockify.me/ui/latest/css/main.min.css`)
- **Interactivity:** Vanilla JS (fetch API for CRUD operations against `/api/*` endpoints)
- **Theme:** Read `theme` claim from JWT, add `dark` class to body
- **Localization:** English message bundle with server-provided `language` and `timezone` from verified JWT claims
- **Token refresh:** `window.parent.postMessage({title:'refreshAddonToken'}, parentOrigin)` every 25 minutes, plus one refresh/retry on `401`
- **Parent origin detection:** `location.ancestorOrigins[0]` first, then `document.referrer`; postMessage-only features are disabled if origin cannot be derived safely
- **Accessibility:** modal focus trap + restore, explicit button/input types, and server-rendered labels/messages
- **No build step:** HTML + CSS + JS served from `/static/` and Thymeleaf templates

### UI State Management

```javascript
// Token from iframe URL
const token = new URLSearchParams(location.search).get('auth_token');
const language = document.body.dataset.language || 'en';
const timezone = document.body.dataset.timezone || '';

// All API calls use the verified addon token
const headers = { 'X-Addon-Token': token, 'Content-Type': 'application/json' };

// CRUD + locale-aware rendering
fetch('/api/actions', { headers }).then(r => r.json()).then(renderActionList);
fetch('/api/actions', { method: 'POST', headers, body: JSON.stringify(action) });
fetch(`/api/actions/${id}/test`, { method: 'POST', headers });
fetch(`/api/actions/${id}/logs`, { headers }).then(r => r.json()).then(renderLogs);
```

## 10. Action JSON Schema (API response)

```json
{
  "id": "a1b2c3d4-...",
  "name": "Billing Sync",
  "eventType": "NEW_TIME_ENTRY",
  "enabled": true,
  "httpMethod": "POST",
  "urlTemplate": "https://billing.co/api/entries",
  "headers": {
    "Authorization": "Bearer sk-xxx",
    "Content-Type": "application/json"
  },
  "bodyTemplate": "{\n  \"user\": \"{{event.userId}}\",\n  \"duration\": \"{{event.timeInterval.duration}}\"\n}",
  "hasSigning": false,
  "retryCount": 3,
  "successConditions": [],
  "chainOrder": null,
  "executionConditions": [],
  "cronExpression": null,
  "createdAt": "2026-03-27T10:00:00Z",
  "updatedAt": "2026-03-27T10:00:00Z"
}
```

## 11. Execution Log Entry Schema

```json
{
  "id": 12345,
  "actionId": "a1b2c3d4-...",
  "eventType": "NEW_TIME_ENTRY",
  "requestUrl": "https://billing.co/api/entries",
  "requestMethod": "POST",
  "responseStatus": 200,
  "responseTimeMs": 142,
  "responseBody": "{\"status\":\"ok\"}",
  "errorMessage": null,
  "success": true,
  "executedAt": "2026-03-27T12:04:15Z"
}
```

## 12. Error Handling

| Scenario | Response | Action |
|----------|----------|--------|
| Invalid JWT on lifecycle/webhook | 401 | Log, reject |
| Malformed lifecycle payload | 400 | Reject and surface structured validation error |
| Unknown workspace | 200 | Accept, discard silently |
| Addon status INACTIVE | 200 | Accept webhook, discard silently |
| No matching actions for event | 200 | Accept, discard, increment metric |
| Template variable not found | N/A | Replace with empty string, log warning |
| Outbound URL unreachable | N/A | Retry with backoff, log failure |
| Outbound 4xx response | N/A | Log as failure, no retry (client error) |
| Outbound 5xx response | N/A | Retry with backoff, log each attempt |
| Outbound timeout (30s) | N/A | Log as failure, retry |
| Action URL not HTTPS (prod) | 400 | Reject action creation |
| Too many actions (>50/workspace) | 400 | Reject creation |
| Event type not in manifest webhooks | 400 | Reject action creation |
| Malformed JSON request body | 400 | Reject request with structured API error |
| Request body exceeds 1 MB | 413 | Reject before controller execution |
| Unexpected internal state | 500 | Return generic structured API error |

### Scheduled Action Notes

- Scheduled actions now require an active installation for every event type, not just `NEW_TIME_ENTRY`.
- `lastScheduledRun` is persisted only after the action has a runnable payload, so failed payload preparation does not consume the cron occurrence.

### Chained Action Execution

Actions with a non-null `chainOrder` execute sequentially in ascending `chainOrder` (ties broken by `id`). Actions with null `chainOrder` are independent and execute in parallel.

**`prev.*` template variables:** Each chained action can reference the previous action's HTTP response via `{{prev.status}}`, `{{prev.body}}`, and `{{prev.headers.<Name>}}`.

**Skip semantics:** When a chained action is skipped because its execution conditions are not met, the `prev.*` variables are **not cleared**. The next chained action in sequence receives the result from the **last successfully executed** action, not the immediately preceding one in the chain. For example, if chain is `A -> B -> C` and B is skipped, action C sees action A's result via `prev.*`.

**Chain breakage:** If a chained action fails (returns null result or non-success status), the chain breaks and subsequent actions are not executed.

### Token Redaction in Execution Logs

When `{{meta.installationToken}}` is used in URL or body templates, the interpolated installation token is redacted from `execution_logs.request_url` before persistence. JWT-format tokens are replaced with `[REDACTED]`.
