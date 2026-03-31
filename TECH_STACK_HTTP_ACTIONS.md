# Clockify HTTP Actions — Tech Stack

## Runtime

- Java 21
- Spring Boot 3.5.13
- Spring MVC + Thymeleaf
- Spring Data JPA + Hibernate
- Embedded Tomcat 10.1.53
- PostgreSQL 16
- Flyway for schema migrations
- Caffeine for local caching and rate-limit buckets
- Apache HttpClient 5 behind Spring `RestClient`

## Core Components

- `LifecycleController`: install, delete, status, and settings lifecycle hooks
- `WebhookController` + `WebhookRequestService`: thin webhook endpoint plus auth/idempotency/token verification service
- `ActionApiController`: CRUD, test-fire, logs, import/export, events, templates
- `HealthController`: DB connectivity health check at `/api/health`
- `AddonClaimsFilter` + `VerifiedAddonContext` resolver: shared JWT parsing for `/api/*`
- `RequestCorrelationFilter`: propagates `X-Request-Id`
- `RequestSizeLimitFilter`: 1 MB request guard for `/api/*` and `/webhook/*`
- `RateLimitFilter`: per-workspace/per-group throttling with `/api/health` bypass
- `IframeViewSupport`: shared sidebar/widget claim extraction and iframe headers
- `ExecutionService`: interpolation, execution ordering, retry schedule, logging, header CRLF stripping
- `HttpActionExecutor`: outbound HTTP with SSRF checks and pinned DNS resolution
- `ScheduledActionService`: cron evaluation and optional Clockify data fetch for scheduled runs
- `CleanupService`: batched log/event purge with advisory locks for multi-instance safety

## Security Posture

- Clockify JWTs are verified with RSA public-key signature validation
- Installation tokens, webhook tokens, headers, and signing secrets are AES-encrypted at rest
- Encryption key/salt validated as hex format at startup (not just length)
- Request bodies above 1 MB are rejected before controller processing
- Condition operators validated against allowlist on save (prevents silent evaluation failures)
- Header values have CRLF characters stripped after template interpolation (injection defense)
- Outbound URLs are validated against private/link-local/metadata ranges before execution
- Scheduled action API calls enforce HTTPS-only as defense-in-depth
- DNS resolution is pinned from validation through connection creation
- JWT-format tokens are redacted from execution log URLs before persistence
- `ClockifyUrlNormalizer` canonicalizes `backendUrl`/`apiUrl` claims and installation URLs
- Global response headers:
  - `Strict-Transport-Security: max-age=31536000; includeSubDomains`
  - `X-Content-Type-Options: nosniff`
  - `Referrer-Policy: no-referrer`
  - `Permissions-Policy` disables unused browser capabilities
  - `X-Frame-Options: DENY` for non-embedded endpoints (iframe paths exempt)
- `/api/*` errors are normalized to structured JSON via `@RestControllerAdvice`
- Production Actuator exposure is reduced to health only; public liveness checks use `/api/health`

## Persistence

- `actions.success_conditions` and `actions.conditions` are stored as `jsonb`
- `actions.(workspace_id, name)` has a unique constraint to prevent duplicate action names per workspace
- `webhook_events.event_id` is `VARCHAR(256)`
- `webhook_events.received_at` is `TIMESTAMPTZ NOT NULL`
- `actions.chain_order` is constrained to `1..100` when present
- Webhook idempotency stores an event fingerprint derived from `eventType + ":" + raw payload` and dedupes via `INSERT ... ON CONFLICT DO NOTHING`

## UI

- Server-rendered Thymeleaf templates
- Vanilla JavaScript for CRUD, dynamic event loading, logs with pagination, testing, import/export, and widget JSON polling (with backoff)
- English-only message bundle in `src/main/resources/messages.properties`; `language` claims are preserved but no locale packs ship yet
- Client-side cron expression validation (6-field format check)
- `language` and `timezone` claims are passed from verified JWTs into the views
- Sidebar and widget strip `auth_token` from the URL after the initial render
- Sidebar refreshes addon tokens on timer and on `401`, disables mutable UI during refresh, and falls back to an expired-session state if refresh fails
- Modal interactions include focus trap/restore, and badge/theme colors are shared through CSS custom properties

## Retry / Execution Behavior

- Retry schedule: base-2 exponential backoff (`1s`, `2s`, `4s`, `8s`, ...) capped at `30s` per delay, until `retryCount` is exhausted or the 60-second retry window closes
- Total retry window capped at 60 seconds
- No retries for 4xx responses
- Independent actions execute in parallel
- Chained actions execute in ascending `chainOrder`; when a chained action is skipped (execution conditions not met), `prev.*` variables retain the last successful result
- Rate-limited responses include `Retry-After: 1` header per RFC 6585

## Testing

- Unit + MVC + integration coverage, with Testcontainers integration tests guarded when Docker is unavailable
- Unit tests for services, filters, validation, DNS pinning (IDN normalization), and exception handlers
- MVC tests for controllers and manifest/UI behavior
- Testcontainers-backed integration coverage for Flyway + Postgres startup and idempotency paths
- Current verified baseline on 2026-03-31: `289` tests passing, `1` Docker-gated integration test skipped when Docker is unavailable

## Build / Audit

- `./mvnw verify` runs OWASP Dependency-Check during the Maven `verify` phase
- `dependency-check-suppressions.xml` documents the `angus-activation` false positive for `CVE-2025-7962`
- The OSS Index analyzer is disabled because anonymous `401`/`429` responses made `verify` non-deterministic; NVD/CISA analysis remains enabled
- Docker base images are pinned by digest, and Docker/Railway health checks use `/api/health`
