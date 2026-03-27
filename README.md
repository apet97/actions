# Clockify HTTP Actions

A Clockify Marketplace addon that turns webhook events into configurable outbound HTTP requests. Postman meets Zapier, embedded in Clockify's sidebar.

## Quick Start

```bash
cp .env.example .env
# Edit .env with your ngrok URL, DB settings, and generate encryption keys:
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
./mvnw test                      # run 257 tests
./mvnw verify                    # unit + integration tests (requires Docker)
./mvnw dependency-check:check    # OWASP CVE scan (CVSS >= 7 fails)
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
| GET/POST | `/api/actions/export`, `/import` | Bulk import/export |
| GET | `/api/templates` | Pre-built templates |

## Architecture

```
Clockify Webhook --> WebhookController --> ExecutionService --> HttpActionExecutor --> Target URL
                     (JWT verify,          (parallel exec,     (HMAC signing,
                      idempotency,          retry w/ backoff,   response capture)
                      rate limiting)        conditions)
```

- **Spring Boot 3.5.0** / Java 21 with virtual threads, Tomcat 10.1.46
- **PostgreSQL 16** for persistence, **Caffeine** for sliding-window rate limiting and caching
- **Flyway** for migrations, Apache HttpClient 5 for pinned-DNS outbound calls
- **AES-256** encryption for all stored tokens and secrets
- **SSRF protection** with DNS pinning, CIDR checks, cloud metadata blocking, IDN normalization
- **CSP + HSTS preload**, JWT RS256 alg verification, HMAC replay protection
- **257 tests** (unit + integration), OWASP dependency scanning
- **10 webhook types** registered in manifest; events without actions are accepted and discarded

See [SPEC.md](SPEC.md) for full specification.
