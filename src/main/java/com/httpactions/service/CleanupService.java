package com.httpactions.service;

import com.httpactions.repository.ExecutionLogRepository;
import com.httpactions.repository.WebhookEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Service
public class CleanupService {

    private static final Logger log = LoggerFactory.getLogger(CleanupService.class);

    private final ExecutionLogRepository executionLogRepository;
    private final WebhookEventRepository webhookEventRepository;

    public CleanupService(ExecutionLogRepository executionLogRepository,
                          WebhookEventRepository webhookEventRepository) {
        this.executionLogRepository = executionLogRepository;
        this.webhookEventRepository = webhookEventRepository;
    }

    @Scheduled(cron = "0 0 3 * * *") // 3 AM daily
    @Transactional
    public void cleanupOldLogs() {
        Instant cutoff = Instant.now().minus(30, ChronoUnit.DAYS);
        int deleted = executionLogRepository.deleteByExecutedAtBefore(cutoff);
        if (deleted > 0) {
            log.info("Cleaned up {} execution logs older than 30 days", deleted);
        }
    }

    @Scheduled(cron = "0 30 3 * * *") // 3:30 AM daily
    @Transactional
    public void cleanupOldEvents() {
        Instant cutoff = Instant.now().minus(7, ChronoUnit.DAYS);
        int deleted = webhookEventRepository.deleteByReceivedAtBefore(cutoff);
        if (deleted > 0) {
            log.info("Cleaned up {} webhook events older than 7 days", deleted);
        }
    }
}
