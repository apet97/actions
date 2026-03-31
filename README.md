# Clockify HTTP Actions

A Clockify Marketplace addon that turns webhook events into configurable outbound HTTP requests. Postman meets Zapier, embedded in Clockify's sidebar.

See [SPEC.md](SPEC.md) for the behavior contract and API surface, and [TECH_STACK_HTTP_ACTIONS.md](TECH_STACK_HTTP_ACTIONS.md) for runtime/security/build notes.

## Quick Start

```bash
cp .env.example .env
# Edit .env with your ngrok URL, DB settings, DB password, and generate encryption keys:
#   openssl rand -hex 32  (for TOKEN_ENCRYPTION_KEY)
#   openssl rand -hex 16  (for TOKEN_ENCRYPTION_SALT)
docker compose up
```

The addon will be available at `http://localhost:8080`. Point your ngrok tunnel at it and configure the base URL in `.env`.

## Environment Variables

| Variable | Required | Description |
|----------|----------|-------------|
| `ADDON_KEY` | Yes | Manifest key (default: `clockify-http-actions`) |
| `ADDON_BASE_URL` | Yes | Public URL (ngrok in dev) |
| `DB_URL` | Yes | PostgreSQL JDBC URL |
| `DB_USER` | Yes | Database username |
| `DB_PASSWORD` | Yes | Database password |
| `TOKEN_ENCRYPTION_KEY` | Yes | 64-char hex string for AES-256 encryption |
| `TOKEN_ENCRYPTION_SALT` | Yes | 32-char hex string for encryption salt |

## Development

```bash
./mvnw compile                   # compile
./mvnw test                      # full suite; Flyway/Testcontainers test auto-skips without Docker
./mvnw verify                    # CI-style build; includes OWASP dependency-check
./mvnw spring-boot:run           # run locally (requires Postgres + env vars)
```

## Deploy to Railway

```bash
railway login
railway init
railway add              # Add PostgreSQL
railway variables set SPRING_PROFILES_ACTIVE=prod
railway variables set ADDON_BASE_URL=https://<railway-domain>
railway variables set TOKEN_ENCRYPTION_KEY=$(openssl rand -hex 32)
railway variables set TOKEN_ENCRYPTION_SALT=$(openssl rand -hex 16)
railway up
```

Or connect Railway to this GitHub repo via the dashboard for auto-deploy.

Railway and Docker health checks target `GET /api/health`. In the `prod` profile, public Actuator exposure is reduced to health only; Prometheus scraping is not exposed publicly.

## API Endpoints

| Method | Path | Purpose |
|--------|------|---------|
| GET | `/manifest` | Addon manifest |
| GET | `/sidebar` | Sidebar iframe |
| GET | `/widget` | Widget dashboard |
| POST | `/lifecycle/{type}` | Lifecycle events |
| POST | `/webhook/{slug}` | Webhook receiver |
| GET/POST/PUT/DELETE | `/api/actions[/{id}]` | Action CRUD |
| POST | `/api/actions/{id}/test` | Test-fire action |
| GET | `/api/actions/{id}/logs` | Action execution logs |
| GET | `/api/logs` | Workspace-wide logs |
| GET | `/api/events` | Event types + schemas |
| GET | `/api/actions/export` | Export actions |
| POST | `/api/actions/import` | Import actions |
| GET | `/api/templates` | Pre-built templates |
| GET | `/api/widget/stats` | Widget JSON stats feed |
| GET | `/api/health` | Health check (DB) |

## Architecture

```
Clockify Webhook --> WebhookController --> WebhookRequestService --> ExecutionService --> HttpActionExecutor --> Target URL
                     (thin HTTP layer)      (auth, dedupe,      (parallel exec,     (HMAC signing,
                                            token checks)       retry w/ backoff,   response capture)
                                                                 conditions)
```

- **Spring Boot 3.5.13** / Java 21 with virtual threads, Tomcat 10.1.53
- **PostgreSQL 16** for persistence, **Caffeine** for sliding-window rate limiting and caching
- **Flyway** for migrations, Apache HttpClient 5 for pinned-DNS outbound calls
- **Shared addon auth plumbing** via `AddonClaimsFilter`, `VerifiedAddonContext`, and an MVC argument resolver
- **Request hardening** with `X-Request-Id`, 1 MB request-body enforcement on `/api/*`, `/webhook/*`, and `/lifecycle/*`, structured API errors, and health-check bypass for auth/rate limiting
- **Fail-closed lifecycle validation** that requires matching `workspaceId` values in both the lifecycle JWT claims and request body, and rejects malformed lifecycle payloads with `400`
- **AES-256** encryption for all stored tokens and secrets
- **SSRF protection** with DNS pinning, CIDR checks, cloud metadata blocking, IDN normalization
- **CSP + HSTS + nosniff**, JWT RS256 verification, payload-fingerprint webhook idempotency, HMAC signing, and header CRLF injection defense
- **Action save-time validation** for required `httpMethod`/`urlTemplate` fields plus malformed absolute URL rejection before execution
- **289 unit, MVC, and Docker-gated integration tests**, plus OWASP dependency scanning on `verify`
- **10 webhook types** registered in manifest; events without actions are accepted and discarded
- **16 scopes** (8 READ + 8 WRITE) enabling Clockify→Clockify automation
- **Template-driven sidebar UI** with custom HTTP actions, import/export, log pagination, and test-fire support
- **Widget JSON refresh path** via `/api/widget/stats` instead of HTML re-fetching

## UI Scope

- The addon is English-only in this release.
- Verified JWT `language` and `timezone` claims are still passed through to the iframe views for future localization work.
- The sidebar and widget strip `auth_token` from the URL after first load.
- The sidebar refreshes addon tokens on a 25-minute cadence and retries once on `401` before switching to an expired-session state.
- The widget refreshes via `GET /api/widget/stats` with `X-Addon-Token`.

## Template Variables

Actions use `{{variable}}` syntax in URL, headers, and body templates:

| Variable | Description |
|----------|-------------|
| `{{event.*}}` | Webhook event payload fields (e.g. `{{event.id}}`, `{{event.name}}`) |
| `{{meta.workspaceId}}` | Workspace ID from webhook context |
| `{{meta.backendUrl}}` | Clockify API base URL (e.g. `https://api.clockify.me/api`) |
| `{{meta.installationToken}}` | Installation token for Clockify API calls |
| `{{meta.eventType}}` | Event type identifier |
| `{{meta.addonId}}` | Addon ID from JWT claims |
| `{{prev.*}}` | Previous chained action result (`prev.status`, `prev.body`, `prev.headers.*`) |

### Clockify Automation Templates

Pre-built templates that call back into the Clockify REST API:

| Template | Trigger | Creates |
|----------|---------|---------|
| Auto-Create Project for Client | NEW_CLIENT | Project named after the client |
| Auto-Add Task to New Project | NEW_PROJECT | Task on the new project (e.g. Design) |
| Auto-Create Tag for Project | NEW_PROJECT | Tag matching the project name |
| Auto-Add New User to Group | USER_JOINED_WORKSPACE | Adds user to a default group |
| Auto-Create Review Task | TIMER_STOPPED | Review task on the project |

## Docs

- [SPEC.md](SPEC.md): endpoint, data model, and behavior contract
- [TECH_STACK_HTTP_ACTIONS.md](TECH_STACK_HTTP_ACTIONS.md): stack inventory, security posture, and testing model
