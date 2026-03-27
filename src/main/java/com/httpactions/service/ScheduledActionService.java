package com.httpactions.service;

import com.httpactions.model.entity.Action;
import com.httpactions.model.entity.Installation;
import com.httpactions.model.enums.EventType;
import com.httpactions.repository.ActionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

/**
 * Evaluates cron-based scheduled actions every minute.
 * For each action whose cron expression indicates it should fire now,
 * fetches relevant data from the Clockify REST API and passes through
 * the standard execution pipeline.
 */
@Service
public class ScheduledActionService {

    private static final Logger log = LoggerFactory.getLogger(ScheduledActionService.class);

    private final ActionRepository actionRepository;
    private final ExecutionService executionService;
    private final InstallationService installationService;
    private final TokenService tokenService;
    private final RestClient restClient;
    private final JdbcTemplate jdbcTemplate;

    public ScheduledActionService(ActionRepository actionRepository, ExecutionService executionService,
                                  InstallationService installationService, TokenService tokenService,
                                  RestClient restClient, JdbcTemplate jdbcTemplate) {
        this.actionRepository = actionRepository;
        this.executionService = executionService;
        this.installationService = installationService;
        this.tokenService = tokenService;
        this.restClient = restClient;
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Check every 60 seconds for scheduled actions that are due.
     */
    @Scheduled(fixedRate = 60000)
    @Transactional
    public void evaluateScheduledActions() {
        // Acquire transaction-scoped advisory lock — auto-released on commit/rollback (C4 fix)
        Boolean lockAcquired = jdbcTemplate.queryForObject(
                "SELECT pg_try_advisory_xact_lock(hashtext('scheduled-action-processor'))", Boolean.class);
        if (!Boolean.TRUE.equals(lockAcquired)) {
            log.debug("Could not acquire advisory lock for scheduled actions — another instance is processing");
            return;
        }

        processScheduledActions();
    }

    private void processScheduledActions() {
        List<Action> scheduledActions = actionRepository.findByEnabledTrueAndCronExpressionIsNotNull();
        if (scheduledActions.isEmpty()) {
            return;
        }

        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);

        for (Action action : scheduledActions) {
            try {
                if (!isDue(action, now)) {
                    continue;
                }

                MDC.put("workspaceId", action.getWorkspaceId());
                MDC.put("actionId", action.getId().toString());
                log.info("Scheduled action {} is due, executing", action.getId());

                // M4: update lastScheduledRun BEFORE processing to prevent re-triggering
                // even if async processing fails (the action will retry on next cron tick)
                action.setLastScheduledRun(Instant.now());
                actionRepository.save(action);

                String payload = fetchPayloadForEvent(action);
                if (payload == null) {
                    log.warn("Skipping scheduled action {} because Clockify payload fetch failed", action.getId());
                    continue;
                }
                // Defer async dispatch to after-commit so lastScheduledRun is visible to other instances
                final String wsId = action.getWorkspaceId();
                final EventType et = action.getEventType();
                final String p = payload;
                if (TransactionSynchronizationManager.isSynchronizationActive()) {
                    TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                        @Override
                        public void afterCommit() {
                            executionService.processWebhookAsync(wsId, et, p);
                        }
                    });
                } else {
                    executionService.processWebhookAsync(wsId, et, p);
                }

            } catch (Exception e) {
                log.error("Error evaluating scheduled action {}: {}", action.getId(), e.getMessage(), e);
            } finally {
                MDC.clear();
            }
        }
    }

    /**
     * Determine whether the action is due to run now, based on its cron expression
     * and last run time.
     */
    private boolean isDue(Action action, LocalDateTime now) {
        try {
            CronExpression cron = CronExpression.parse(action.getCronExpression());
            LocalDateTime nextFromLastRun;

            if (action.getLastScheduledRun() != null) {
                LocalDateTime lastRun = LocalDateTime.ofInstant(action.getLastScheduledRun(), ZoneOffset.UTC);
                nextFromLastRun = cron.next(lastRun);
            } else {
                // Never run before — use a time far enough in the past to guarantee triggering
                nextFromLastRun = cron.next(now.minusDays(1));
            }

            // Due if the next execution time is at or before now
            return nextFromLastRun != null && !nextFromLastRun.isAfter(now);
        } catch (IllegalArgumentException e) {
            log.warn("Invalid cron expression '{}' for action {}: {}",
                    action.getCronExpression(), action.getId(), e.getMessage());
            return false;
        }
    }

    /**
     * Fetch a payload for the scheduled action based on its event type.
     * For NEW_TIME_ENTRY: fetches today's time entries from the Clockify REST API.
     * For all other event types: returns an empty JSON object.
     */
    private String fetchPayloadForEvent(Action action) {
        EventType eventType = action.getEventType();

        if (eventType == EventType.NEW_TIME_ENTRY) {
            return fetchTodayTimeEntries(action.getWorkspaceId());
        }

        // Other scheduled events do not require an API fetch yet.
        return "{}";
    }

    /**
     * GET time entries for today from the Clockify REST API using the installation token.
     */
    private String fetchTodayTimeEntries(String workspaceId) {
        Optional<Installation> installationOpt = installationService.getActiveInstallation(workspaceId);
        if (installationOpt.isEmpty()) {
            log.warn("No active installation found for workspace {} — skipping scheduled execution", workspaceId);
            return null;
        }

        Installation installation = installationOpt.get();
        String addonToken = tokenService.decrypt(installation.getAuthTokenEnc());
        String apiUrl = installation.getApiUrl();

        if (addonToken == null || apiUrl == null || apiUrl.isBlank()) {
            log.warn("Missing addon token or API URL for workspace {} — skipping scheduled execution", workspaceId);
            return null;
        }

        // Normalize API URL: ensure it ends with /api/v1
        String baseUrl = apiUrl.replaceAll("/+$", "");
        if (!baseUrl.endsWith("/v1")) {
            if (baseUrl.endsWith("/api")) {
                baseUrl = baseUrl + "/v1";
            } else {
                baseUrl = baseUrl + "/api/v1";
            }
        }

        // Build date range for today
        String todayStart = LocalDateTime.now(ZoneOffset.UTC).toLocalDate()
                .atStartOfDay().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME) + "Z";

        try {
            // M5: use workspace-wide time-entries endpoint (addon token works without userId)
            String url = baseUrl + "/workspaces/" + workspaceId
                    + "/time-entries?start=" + todayStart;

            String response = restClient.get()
                    .uri(url)
                    .header("X-Addon-Token", addonToken)
                    .header("Accept", "application/json")
                    .retrieve()
                    .body(String.class);

            if (response != null) {
                return response;
            }
        } catch (Exception e) {
            log.warn("Failed to fetch time entries for workspace {}: {}", workspaceId, e.getMessage());
        }

        return null;
    }
}
