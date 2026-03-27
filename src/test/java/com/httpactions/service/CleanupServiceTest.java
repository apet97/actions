package com.httpactions.service;

import com.httpactions.repository.ExecutionLogRepository;
import com.httpactions.repository.WebhookEventRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CleanupServiceTest {

    @Mock
    private ExecutionLogRepository executionLogRepository;

    @Mock
    private WebhookEventRepository webhookEventRepository;

    @InjectMocks
    private CleanupService cleanupService;

    @Test
    void cleanupOldLogsCallsRepositoryWithCutoff30DaysAgo() {
        when(executionLogRepository.deleteByExecutedAtBefore(any(Instant.class))).thenReturn(5);

        cleanupService.cleanupOldLogs();

        ArgumentCaptor<Instant> captor = ArgumentCaptor.forClass(Instant.class);
        verify(executionLogRepository).deleteByExecutedAtBefore(captor.capture());

        Instant cutoff = captor.getValue();
        Instant expected = Instant.now().minus(30, ChronoUnit.DAYS);

        // The cutoff should be approximately 30 days ago (within 1 minute tolerance)
        assertTrue(Duration.between(cutoff, expected).abs().toMinutes() < 1,
                "Cutoff should be approximately 30 days ago, but was: " + cutoff);
    }

    @Test
    void cleanupOldEventsCallsRepositoryWithCutoff7DaysAgo() {
        when(webhookEventRepository.deleteByReceivedAtBefore(any(Instant.class))).thenReturn(3);

        cleanupService.cleanupOldEvents();

        ArgumentCaptor<Instant> captor = ArgumentCaptor.forClass(Instant.class);
        verify(webhookEventRepository).deleteByReceivedAtBefore(captor.capture());

        Instant cutoff = captor.getValue();
        Instant expected = Instant.now().minus(7, ChronoUnit.DAYS);

        // The cutoff should be approximately 7 days ago (within 1 minute tolerance)
        assertTrue(Duration.between(cutoff, expected).abs().toMinutes() < 1,
                "Cutoff should be approximately 7 days ago, but was: " + cutoff);
    }

    @Test
    void cleanupOldLogsHandlesExceptionGracefully() {
        when(executionLogRepository.deleteByExecutedAtBefore(any(Instant.class)))
                .thenThrow(new RuntimeException("DB connection lost"));

        // The exception propagates because CleanupService does not catch it internally;
        // Spring's @Scheduled handles it. We verify the repository was called.
        assertThrows(RuntimeException.class, () -> cleanupService.cleanupOldLogs());

        verify(executionLogRepository).deleteByExecutedAtBefore(any(Instant.class));
    }

    @Test
    void cleanupOldEventsHandlesExceptionGracefully() {
        when(webhookEventRepository.deleteByReceivedAtBefore(any(Instant.class)))
                .thenThrow(new RuntimeException("DB connection lost"));

        // Same as above: exception propagates, Spring @Scheduled handles it
        assertThrows(RuntimeException.class, () -> cleanupService.cleanupOldEvents());

        verify(webhookEventRepository).deleteByReceivedAtBefore(any(Instant.class));
    }

    @Test
    void cleanupOldLogsWithZeroDeletedDoesNotFail() {
        when(executionLogRepository.deleteByExecutedAtBefore(any(Instant.class))).thenReturn(0);

        assertDoesNotThrow(() -> cleanupService.cleanupOldLogs());

        verify(executionLogRepository).deleteByExecutedAtBefore(any(Instant.class));
    }

    @Test
    void cleanupOldEventsWithZeroDeletedDoesNotFail() {
        when(webhookEventRepository.deleteByReceivedAtBefore(any(Instant.class))).thenReturn(0);

        assertDoesNotThrow(() -> cleanupService.cleanupOldEvents());

        verify(webhookEventRepository).deleteByReceivedAtBefore(any(Instant.class));
    }
}
