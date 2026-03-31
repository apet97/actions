package com.httpactions.service;

import com.google.gson.JsonObject;
import com.httpactions.config.GsonProvider;
import com.httpactions.model.dto.TestResult;
import com.httpactions.model.entity.Action;
import com.httpactions.model.entity.ExecutionLog;
import com.httpactions.model.enums.EventType;
import com.httpactions.repository.ActionRepository;
import com.httpactions.repository.ExecutionLogRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.LongSupplier;

@Service
public class ExecutionService {

    private static final Logger log = LoggerFactory.getLogger(ExecutionService.class);

    private final ActionService actionService;
    private final ActionRepository actionRepository;
    private final VariableInterpolator templateEngine;
    private final HttpActionExecutor httpActionExecutor;
    private final TokenService tokenService;
    private final InstallationService installationService;
    private final ExecutionLogRepository executionLogRepository;
    private final ConditionEvaluator conditionEvaluator;
    private final MeterRegistry meterRegistry;
    private final Executor taskExecutor;
    private final Clock clock;
    private final Sleeper sleeper;
    private final LongSupplier nanoTimeSupplier;

    public ExecutionService(ActionService actionService, ActionRepository actionRepository,
                            VariableInterpolator templateEngine,
                            HttpActionExecutor httpActionExecutor, TokenService tokenService,
                            InstallationService installationService,
                            ExecutionLogRepository executionLogRepository,
                            ConditionEvaluator conditionEvaluator,
                            MeterRegistry meterRegistry,
                            Executor taskExecutor,
                            Clock clock,
                            Sleeper sleeper,
                            LongSupplier nanoTimeSupplier) {
        this.actionService = actionService;
        this.actionRepository = actionRepository;
        this.templateEngine = templateEngine;
        this.httpActionExecutor = httpActionExecutor;
        this.tokenService = tokenService;
        this.installationService = installationService;
        this.executionLogRepository = executionLogRepository;
        this.conditionEvaluator = conditionEvaluator;
        this.meterRegistry = meterRegistry;
        this.taskExecutor = taskExecutor;
        this.clock = clock;
        this.sleeper = sleeper;
        this.nanoTimeSupplier = nanoTimeSupplier;
    }

    /**
     * Process a webhook event asynchronously.
     * Called after returning 200 to Clockify.
     *
     * Actions are split into two groups:
     * - Independent (chainOrder == null): executed in parallel (current behavior, sequentially in this thread)
     * - Chained (chainOrder != null): executed sequentially in chainOrder, with prev.* template vars
     *
     * Note on Counter.builder().register() usage: Micrometer's MeterRegistry caches meters internally,
     * so calling Counter.builder(...).register(meterRegistry) on every invocation does NOT create new
     * meter instances each time. The registry returns the existing meter if one with the same name and
     * tags already exists. This pattern is idiomatic for counters with dynamic tags (e.g., eventType,
     * reason, success) where pre-registering all tag combinations in the constructor is impractical.
     */
    @Async
    public void processWebhookAsync(String workspaceId, EventType eventType, String payload) {
        MDC.put("workspaceId", workspaceId);
        MDC.put("eventType", eventType.name());

        try {
            Counter.builder("clockify.webhook.received")
                    .tag("eventType", eventType.name())
                    .register(meterRegistry)
                    .increment();

            List<Action> actions = actionRepository
                    .findByWorkspaceIdAndEventTypeAndEnabledTrueOrderByChainOrderAscIdAsc(workspaceId, eventType);
            if (actions.isEmpty()) {
                log.debug("No enabled actions for event {} in workspace {}", eventType, workspaceId);
                Counter.builder("clockify.webhook.no_actions")
                        .tag("eventType", eventType.name())
                        .register(meterRegistry)
                        .increment();
                return;
            }

            JsonObject event = GsonProvider.get().fromJson(payload, JsonObject.class);
            Map<String, String> meta = buildMeta(workspaceId, eventType);

            // Separate independent actions (chainOrder == null) from chained actions
            List<Action> independentActions = new ArrayList<>();
            List<Action> chainedActions = new ArrayList<>();
            for (Action action : actions) {
                if (action.getChainOrder() == null) {
                    independentActions.add(action);
                } else {
                    chainedActions.add(action);
                }
            }

            // Execute independent actions in parallel (no prev vars)
            Map<String, String> mdcContext = MDC.getCopyOfContextMap();
            List<CompletableFuture<Void>> futures = new ArrayList<>();
            for (Action action : independentActions) {
                if (!conditionEvaluator.evaluateExecutionConditions(action.getExecutionConditions(), event)) {
                    log.debug("Execution conditions not met for action {} in workspace {}, skipping",
                            action.getId(), workspaceId);
                    Counter.builder("clockify.action.skipped")
                            .tag("eventType", eventType.name())
                            .tag("reason", "conditions_not_met")
                            .register(meterRegistry)
                            .increment();
                    continue;
                }

                // H5: use configured virtual thread executor instead of ForkJoinPool.commonPool()
                futures.add(CompletableFuture.runAsync(() -> {
                    if (mdcContext != null) MDC.setContextMap(mdcContext);
                    MDC.put("actionId", action.getId().toString());
                    try {
                        executeAction(action, event, meta, payload, null);
                    } catch (Exception e) {
                        log.error("Failed to execute independent action {}: {}", action.getId(), e.getMessage());
                    } finally {
                        MDC.clear();
                    }
                }, taskExecutor));
            }
            if (!futures.isEmpty()) {
                CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
            }

            // Execute chained actions sequentially, passing prev result forward
            TestResult prevResult = null;
            for (Action action : chainedActions) {
                MDC.put("actionId", action.getId().toString());

                if (!conditionEvaluator.evaluateExecutionConditions(action.getExecutionConditions(), event)) {
                    log.debug("Execution conditions not met for chained action {} (chainOrder={}), skipping",
                            action.getId(), action.getChainOrder());
                    Counter.builder("clockify.action.skipped")
                            .tag("eventType", eventType.name())
                            .tag("reason", "conditions_not_met")
                            .register(meterRegistry)
                            .increment();
                    MDC.remove("actionId");
                    // Do NOT clear prevResult — next chained action still sees the last successful result
                    continue;
                }

                Map<String, String> prevVars = buildPrevVars(prevResult);
                prevResult = executeAction(action, event, meta, payload, prevVars);
                MDC.remove("actionId");

                // Break chain on failure — null means exception, !isSuccess means HTTP error
                if (prevResult == null || !prevResult.isSuccess()) {
                    log.warn("Chain broken at action {} (chainOrder={}) due to failure",
                            action.getId(), action.getChainOrder());
                    Counter.builder("clockify.chain.broken")
                            .tag("eventType", eventType.name())
                            .register(meterRegistry)
                            .increment();
                    break;
                }
            }

        } catch (Exception e) {
            log.error("Error processing webhook for workspace {}: {}", workspaceId, e.getMessage(), e);
        } finally {
            MDC.clear();
        }
    }

    /**
     * Test-fire an action with sample data (synchronous).
     */
    public TestResult testAction(Action action, String samplePayload) {
        JsonObject event = GsonProvider.get().fromJson(samplePayload, JsonObject.class);
        Map<String, String> meta = buildMeta(action.getWorkspaceId(), action.getEventType());

        String url = templateEngine.interpolate(action.getUrlTemplate(), event, meta, true);
        Map<String, String> headers = decryptHeaders(action);
        addInstallationTokenIfNeeded(action, headers, meta);
        if (headers != null) {
            Map<String, String> interpolatedHeaders = new HashMap<>();
            headers.forEach((k, v) -> {
                String interpolated = templateEngine.interpolate(v, event, meta, false);
                if (interpolated != null) {
                    interpolated = interpolated.replaceAll("[\\r\\n]", "");
                }
                interpolatedHeaders.put(k, interpolated);
            });
            headers = interpolatedHeaders;
        }
        String body = templateEngine.interpolate(action.getBodyTemplate(), event, meta, false);
        String signingSecret = action.getSigningSecretEnc() != null
                ? tokenService.decrypt(action.getSigningSecretEnc()) : null;

        // Execute
        TestResult result = httpActionExecutor.execute(
                action.getHttpMethod().name(), url, headers, body, signingSecret);

        // Evaluate success conditions on full body (override default 2xx check)
        boolean conditionResult = conditionEvaluator.evaluateSuccessConditions(
                action.getSuccessConditions(), result);
        result.setSuccess(conditionResult);

        // Truncate response body after condition evaluation
        truncateResponseBody(result);

        // Log execution
        saveExecutionLog(action, url, result);

        return result;
    }

    /**
     * Execute a single action with optional previous-action variables for chaining.
     *
     * @param action     The action to execute
     * @param event      Parsed webhook event payload
     * @param meta       Metadata map
     * @param rawPayload Raw JSON payload string
     * @param prevVars   Variables from the previous chained action (nullable for independent actions)
     * @return The TestResult, or null if execution failed with an exception
     */
    private TestResult executeAction(Action action, JsonObject event, Map<String, String> meta,
                                     String rawPayload, Map<String, String> prevVars) {
        try {
            Map<String, String> headers = decryptHeaders(action);
            addInstallationTokenIfNeeded(action, headers, meta);
            String url = templateEngine.interpolate(action.getUrlTemplate(), event, meta, true, prevVars);
            if (headers != null) {
                Map<String, String> interpolatedHeaders = new HashMap<>();
                final Map<String, String> pv = prevVars;
                headers.forEach((k, v) -> {
                    String interpolated = templateEngine.interpolate(v, event, meta, false, pv);
                    // C3: strip CRLF to prevent header injection via template variables
                    if (interpolated != null) {
                        interpolated = interpolated.replaceAll("[\\r\\n]", "");
                    }
                    interpolatedHeaders.put(k, interpolated);
                });
                headers = interpolatedHeaders;
            }
            String body = templateEngine.interpolate(action.getBodyTemplate(), event, meta, false, prevVars);
            String signingSecret = action.getSigningSecretEnc() != null
                    ? tokenService.decrypt(action.getSigningSecretEnc()) : null;

            TestResult result = executeWithRetry(action, url, headers, body, signingSecret);

            // Evaluate success conditions on full body (override default 2xx check)
            boolean conditionResult = conditionEvaluator.evaluateSuccessConditions(
                    action.getSuccessConditions(), result);
            result.setSuccess(conditionResult);

            // Truncate response body after condition evaluation for storage and prev.* vars
            truncateResponseBody(result);

            // Log execution
            saveExecutionLog(action, url, result);

            Counter.builder("clockify.action.executed")
                    .tag("eventType", action.getEventType().name())
                    .tag("success", String.valueOf(result.isSuccess()))
                    .register(meterRegistry)
                    .increment();

            return result;

        } catch (Exception e) {
            log.error("Failed to execute action {}: {}", action.getId(), e.getMessage());
            saveErrorLog(action, e.getMessage());
            return null;
        }
    }

    private static final long MAX_RETRY_DURATION_MS = 60_000;

    private TestResult executeWithRetry(Action action, String url, Map<String, String> headers,
                                         String body, String signingSecret) {
        String method = action.getHttpMethod().name();
        TestResult lastResult = null;
        int maxAttempts = action.getRetryCount() + 1; // retryCount = retries, +1 for initial attempt
        long retryStart = nanoTimeSupplier.getAsLong();

        try (HttpActionExecutor.PreparedRequest preparedRequest = httpActionExecutor.prepare(url)) {
            for (int attempt = 1; attempt <= maxAttempts; attempt++) {
                lastResult = httpActionExecutor.execute(preparedRequest, method, url, headers, body, signingSecret);

                if (lastResult.isSuccess()) return lastResult;

                if (lastResult.getResponseStatus() != null
                        && lastResult.getResponseStatus() >= 400
                        && lastResult.getResponseStatus() < 500) {
                    return lastResult;
                }

                if (attempt < maxAttempts) {
                    long elapsed = (nanoTimeSupplier.getAsLong() - retryStart) / 1_000_000L;
                    long remaining = MAX_RETRY_DURATION_MS - elapsed;
                    if (remaining <= 0) {
                        log.debug("Retry duration cap reached for action {} after {}ms", action.getId(), elapsed);
                        return lastResult;
                    }
                    long delay = Math.min((long) Math.pow(2, attempt - 1) * 1000, 30_000);
                    delay = Math.min(delay, remaining);
                    log.debug("Retrying action {} attempt {}/{} after {}ms", action.getId(), attempt + 1, maxAttempts, delay);
                    try {
                        sleeper.sleep(delay);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return lastResult;
                    }
                }
            }
        } catch (Exception e) {
            TestResult errorResult = new TestResult();
            errorResult.setSuccess(false);
            errorResult.setErrorMessage("Unexpected error: " + e.getMessage());
            return errorResult;
        }

        return lastResult;
    }

    private Map<String, String> decryptHeaders(Action action) {
        if (action.getHeadersEnc() == null) return null;
        String headersJson = tokenService.decrypt(action.getHeadersEnc());
        if (headersJson == null) {
            return null;
        }
        return GsonProvider.get().fromJson(headersJson, GsonProvider.STRING_MAP_TYPE);
    }

    /**
     * Build prev.* template variables from a previous action's TestResult.
     * Returns null if the previous result is null (first in chain or prior failure).
     */
    private Map<String, String> buildPrevVars(TestResult prevResult) {
        if (prevResult == null) return null;

        Map<String, String> prevVars = new HashMap<>();
        prevVars.put("prev.status",
                prevResult.getResponseStatus() != null ? String.valueOf(prevResult.getResponseStatus()) : "");
        prevVars.put("prev.body",
                prevResult.getResponseBody() != null ? prevResult.getResponseBody() : "");

        // Flatten response headers as prev.headers.<HeaderName>
        if (prevResult.getResponseHeaders() != null) {
            prevResult.getResponseHeaders().forEach((headerName, headerValue) ->
                    prevVars.put("prev.headers." + headerName, headerValue != null ? headerValue : ""));
        }

        return prevVars;
    }

    private Map<String, String> buildMeta(String workspaceId, EventType eventType) {
        Map<String, String> meta = new HashMap<>();
        meta.put("workspaceId", workspaceId);
        meta.put("eventType", eventType.name());
        meta.put("receivedAt", clock.instant().toString());

        installationService.getActiveInstallation(workspaceId).ifPresent(installation -> {
            meta.put("addonId", installation.getAddonId());
            meta.put("backendUrl", installation.getApiUrl());
        });

        return meta;
    }

    private void addInstallationTokenIfNeeded(Action action, Map<String, String> headers, Map<String, String> meta) {
        if (!usesInstallationToken(action.getUrlTemplate())
                && !usesInstallationToken(action.getBodyTemplate())
                && (headers == null || headers.values().stream().noneMatch(this::usesInstallationToken))) {
            return;
        }

        installationService.getActiveInstallation(action.getWorkspaceId()).ifPresent(installation -> {
            String decryptedToken = tokenService.decrypt(installation.getAuthTokenEnc());
            if (decryptedToken != null) {
                meta.put("installationToken", decryptedToken);
            }
        });
    }

    private boolean usesInstallationToken(String template) {
        return template != null && template.contains("meta.installationToken");
    }

    private void truncateResponseBody(TestResult result) {
        result.setResponseBody(truncateResponseBody(result.getResponseBody()));
    }

    private String truncateResponseBody(String body) {
        if (body != null && body.length() > HttpActionExecutor.MAX_RESPONSE_BODY_SIZE) {
            return body.substring(0, HttpActionExecutor.MAX_RESPONSE_BODY_SIZE) + "...[truncated]";
        }
        return body;
    }

    private void saveExecutionLog(Action action, String url, TestResult result) {
        ExecutionLog logEntry = new ExecutionLog();
        logEntry.setActionId(action.getId());
        logEntry.setWorkspaceId(action.getWorkspaceId());
        logEntry.setEventType(action.getEventType().name());
        logEntry.setRequestUrl(redactTokens(url));
        logEntry.setRequestMethod(action.getHttpMethod().name());
        logEntry.setResponseStatus(result.getResponseStatus());
        logEntry.setResponseTimeMs(result.getResponseTimeMs());
        logEntry.setResponseBody(truncateResponseBody(result.getResponseBody()));
        logEntry.setErrorMessage(result.getErrorMessage());
        logEntry.setSuccess(result.isSuccess());
        executionLogRepository.save(logEntry);
    }

    private String redactTokens(String value) {
        if (value == null) return null;
        // Redact installation tokens that were interpolated via {{meta.installationToken}}.
        // Tokens are JWT-like strings (3 Base64 segments separated by dots, each 20+ chars).
        return value.replaceAll("eyJ[A-Za-z0-9_-]{20,}\\.eyJ[A-Za-z0-9_-]{20,}\\.[A-Za-z0-9_-]{20,}", "[REDACTED]");
    }

    private void saveErrorLog(Action action, String errorMessage) {
        ExecutionLog logEntry = new ExecutionLog();
        logEntry.setActionId(action.getId());
        logEntry.setWorkspaceId(action.getWorkspaceId());
        logEntry.setEventType(action.getEventType().name());
        logEntry.setErrorMessage(errorMessage);
        logEntry.setSuccess(false);
        executionLogRepository.save(logEntry);
    }
}
