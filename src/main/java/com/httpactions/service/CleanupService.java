package com.httpactions.service;

import com.httpactions.repository.ExecutionLogRepository;
import com.httpactions.repository.WebhookEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Service
public class CleanupService {

    private static final Logger log = LoggerFactory.getLogger(CleanupService.class);
    private static final int DELETE_BATCH_SIZE = 5000;

    private final ExecutionLogRepository executionLogRepository;
    private final WebhookEventRepository webhookEventRepository;
    private final TransactionTemplate transactionTemplate;
    private final JdbcTemplate jdbcTemplate;
    private final Clock clock;

    public CleanupService(ExecutionLogRepository executionLogRepository,
                          WebhookEventRepository webhookEventRepository,
                          TransactionTemplate transactionTemplate,
                          JdbcTemplate jdbcTemplate,
                          Clock clock) {
        this.executionLogRepository = executionLogRepository;
        this.webhookEventRepository = webhookEventRepository;
        this.transactionTemplate = transactionTemplate;
        this.jdbcTemplate = jdbcTemplate;
        this.clock = clock;
    }

    @Scheduled(cron = "0 0 3 * * *", zone = "UTC")
    public void cleanupOldLogs() {
        Boolean lockAcquired = transactionTemplate.execute(status ->
                jdbcTemplate.queryForObject(
                        "SELECT pg_try_advisory_xact_lock(hashtext('cleanup-execution-logs'))", Boolean.class));
        if (!Boolean.TRUE.equals(lockAcquired)) {
            log.debug("Could not acquire advisory lock for log cleanup — another instance is processing");
            return;
        }
        Instant cutoff = clock.instant().minus(30, ChronoUnit.DAYS);
        int totalDeleted = 0;
        int deleted;
        do {
            Integer deletedCount = transactionTemplate.execute(status ->
                    executionLogRepository.deleteByExecutedAtBeforeBatch(cutoff, DELETE_BATCH_SIZE));
            deleted = deletedCount == null ? 0 : deletedCount;
            totalDeleted += deleted;
        } while (deleted >= DELETE_BATCH_SIZE);
        if (totalDeleted > 0) {
            log.info("Cleaned up {} execution logs older than 30 days", totalDeleted);
        }
    }

    @Scheduled(cron = "0 30 3 * * *", zone = "UTC")
    public void cleanupOldEvents() {
        Boolean lockAcquired = transactionTemplate.execute(status ->
                jdbcTemplate.queryForObject(
                        "SELECT pg_try_advisory_xact_lock(hashtext('cleanup-webhook-events'))", Boolean.class));
        if (!Boolean.TRUE.equals(lockAcquired)) {
            log.debug("Could not acquire advisory lock for event cleanup — another instance is processing");
            return;
        }
        Instant cutoff = clock.instant().minus(7, ChronoUnit.DAYS);
        int totalDeleted = 0;
        int deleted;
        do {
            Integer deletedCount = transactionTemplate.execute(status ->
                    webhookEventRepository.deleteByReceivedAtBeforeBatch(cutoff, DELETE_BATCH_SIZE));
            deleted = deletedCount == null ? 0 : deletedCount;
            totalDeleted += deleted;
        } while (deleted >= DELETE_BATCH_SIZE);
        if (totalDeleted > 0) {
            log.info("Cleaned up {} webhook events older than 7 days", totalDeleted);
        }
    }
}
