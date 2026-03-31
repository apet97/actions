package com.httpactions.service;

import com.httpactions.repository.ExecutionLogRepository;
import com.httpactions.repository.WebhookEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CleanupServiceTest {

    @Mock
    private ExecutionLogRepository executionLogRepository;

    @Mock
    private WebhookEventRepository webhookEventRepository;

    @Mock
    private TransactionTemplate transactionTemplate;

    @Mock
    private JdbcTemplate jdbcTemplate;

    private Clock clock;

    private CleanupService cleanupService;

    @BeforeEach
    void setUp() {
        // TransactionTemplate.execute must run the callback to delegate to the repository
        lenient().when(transactionTemplate.execute(any())).thenAnswer(invocation -> {
            TransactionCallback<?> callback = invocation.getArgument(0);
            return callback.doInTransaction(null);
        });
        // Advisory lock always acquired in tests
        lenient().when(jdbcTemplate.queryForObject(anyString(), eq(Boolean.class))).thenReturn(true);
        clock = Clock.fixed(Instant.parse("2026-03-30T12:00:00Z"), ZoneOffset.UTC);
        cleanupService = new CleanupService(executionLogRepository, webhookEventRepository,
                transactionTemplate, jdbcTemplate, clock);
    }

    @Test
    void cleanupOldLogsDeletesInBatches() {
        when(executionLogRepository.deleteByExecutedAtBeforeBatch(any(Instant.class), anyInt()))
                .thenReturn(5000)
                .thenReturn(1200);

        cleanupService.cleanupOldLogs();

        verify(executionLogRepository, times(2)).deleteByExecutedAtBeforeBatch(any(Instant.class), anyInt());
    }

    @Test
    void cleanupOldEventsDeletesInBatches() {
        when(webhookEventRepository.deleteByReceivedAtBeforeBatch(any(Instant.class), anyInt()))
                .thenReturn(5000)
                .thenReturn(500);

        cleanupService.cleanupOldEvents();

        verify(webhookEventRepository, times(2)).deleteByReceivedAtBeforeBatch(any(Instant.class), anyInt());
    }

    @Test
    void cleanupOldLogsHandlesExceptionGracefully() {
        when(executionLogRepository.deleteByExecutedAtBeforeBatch(any(Instant.class), anyInt()))
                .thenThrow(new RuntimeException("DB connection lost"));

        assertThrows(RuntimeException.class, () -> cleanupService.cleanupOldLogs());

        verify(executionLogRepository).deleteByExecutedAtBeforeBatch(any(Instant.class), anyInt());
    }

    @Test
    void cleanupOldEventsHandlesExceptionGracefully() {
        when(webhookEventRepository.deleteByReceivedAtBeforeBatch(any(Instant.class), anyInt()))
                .thenThrow(new RuntimeException("DB connection lost"));

        assertThrows(RuntimeException.class, () -> cleanupService.cleanupOldEvents());

        verify(webhookEventRepository).deleteByReceivedAtBeforeBatch(any(Instant.class), anyInt());
    }

    @Test
    void cleanupOldLogsWithZeroDeletedDoesNotFail() {
        when(executionLogRepository.deleteByExecutedAtBeforeBatch(any(Instant.class), anyInt())).thenReturn(0);

        assertDoesNotThrow(() -> cleanupService.cleanupOldLogs());

        verify(executionLogRepository).deleteByExecutedAtBeforeBatch(any(Instant.class), anyInt());
    }

    @Test
    void cleanupOldEventsWithZeroDeletedDoesNotFail() {
        when(webhookEventRepository.deleteByReceivedAtBeforeBatch(any(Instant.class), anyInt())).thenReturn(0);

        assertDoesNotThrow(() -> cleanupService.cleanupOldEvents());

        verify(webhookEventRepository).deleteByReceivedAtBeforeBatch(any(Instant.class), anyInt());
    }

    @Test
    void cleanupOldLogsStopsWhenBatchReturnsFewer() {
        when(executionLogRepository.deleteByExecutedAtBeforeBatch(any(Instant.class), anyInt()))
                .thenReturn(100);

        cleanupService.cleanupOldLogs();

        verify(executionLogRepository, times(1)).deleteByExecutedAtBeforeBatch(any(Instant.class), anyInt());
    }

    @Test
    void cleanupOldLogsLoopsUntilBatchExhausted() {
        when(executionLogRepository.deleteByExecutedAtBeforeBatch(any(Instant.class), anyInt()))
                .thenReturn(5000)
                .thenReturn(5000)
                .thenReturn(5000)
                .thenReturn(2000);

        cleanupService.cleanupOldLogs();

        verify(executionLogRepository, times(4)).deleteByExecutedAtBeforeBatch(any(Instant.class), anyInt());
    }

    @Test
    void eachBatchRunsInItsOwnTransaction() {
        when(executionLogRepository.deleteByExecutedAtBeforeBatch(any(Instant.class), anyInt()))
                .thenReturn(5000)
                .thenReturn(0);

        cleanupService.cleanupOldLogs();

        // 1 call for advisory lock + 2 calls for batch iterations
        verify(transactionTemplate, times(3)).execute(any());
    }
}
