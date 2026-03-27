package com.httpactions.service;

import com.httpactions.model.dto.ExecutionLogResponse;
import com.httpactions.model.dto.WidgetStats;
import com.httpactions.model.entity.ExecutionLog;
import com.httpactions.repository.ActionRepository;
import com.httpactions.repository.ExecutionLogRepository;
import com.httpactions.repository.WebhookEventRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LogServiceTest {

    @Mock
    private ExecutionLogRepository executionLogRepository;

    @Mock
    private ActionRepository actionRepository;

    @Mock
    private WebhookEventRepository webhookEventRepository;

    @InjectMocks
    private LogService logService;

    private static final String WORKSPACE_ID = "ws-test-456";

    // ── getLogsForAction ──

    @Test
    @DisplayName("getLogsForAction returns mapped page")
    void getLogsForAction_returnsMappedPage() {
        UUID actionId = UUID.randomUUID();
        ExecutionLog log1 = createTestLog(actionId, 200, true);
        ExecutionLog log2 = createTestLog(actionId, 500, false);

        Page<ExecutionLog> entityPage = new PageImpl<>(List.of(log1, log2), PageRequest.of(0, 20), 2);
        when(executionLogRepository.findByActionIdOrderByExecutedAtDesc(eq(actionId), any(PageRequest.class)))
                .thenReturn(entityPage);

        Page<ExecutionLogResponse> result = logService.getLogsForAction(actionId, 0, 20);

        assertEquals(2, result.getContent().size());
        assertEquals(200, result.getContent().get(0).getResponseStatus());
        assertTrue(result.getContent().get(0).isSuccess());
        assertEquals(500, result.getContent().get(1).getResponseStatus());
        assertFalse(result.getContent().get(1).isSuccess());
    }

    // ── getLogsForWorkspace ──

    @Test
    @DisplayName("getLogsForWorkspace returns mapped page")
    void getLogsForWorkspace_returnsMappedPage() {
        UUID actionId = UUID.randomUUID();
        ExecutionLog log1 = createTestLog(actionId, 200, true);

        Page<ExecutionLog> entityPage = new PageImpl<>(List.of(log1), PageRequest.of(0, 20), 1);
        when(executionLogRepository.findByWorkspaceIdOrderByExecutedAtDesc(eq(WORKSPACE_ID), any(PageRequest.class)))
                .thenReturn(entityPage);

        Page<ExecutionLogResponse> result = logService.getLogsForWorkspace(WORKSPACE_ID, 0, 20);

        assertEquals(1, result.getContent().size());
        assertEquals("https://example.com/hook", result.getContent().get(0).getRequestUrl());
        assertEquals("POST", result.getContent().get(0).getRequestMethod());
        assertEquals(actionId, result.getContent().get(0).getActionId());
    }

    // ── getWidgetStats ──

    @Test
    @DisplayName("getWidgetStats calculates correctly with executions")
    void getWidgetStats_calculatesCorrectly() {
        when(actionRepository.countByWorkspaceIdAndEnabledTrue(WORKSPACE_ID)).thenReturn(5L);
        when(executionLogRepository.countByWorkspaceIdAndExecutedAtAfter(eq(WORKSPACE_ID), any(Instant.class)))
                .thenReturn(100L);
        when(executionLogRepository.countByWorkspaceIdAndSuccessAndExecutedAtAfter(
                eq(WORKSPACE_ID), eq(false), any(Instant.class)))
                .thenReturn(10L);

        WidgetStats stats = logService.getWidgetStats(WORKSPACE_ID);

        assertEquals(5, stats.getActiveActionCount());
        assertEquals(100, stats.getTotalExecutions24h());
        assertEquals(10, stats.getFailedExecutions24h());
        assertEquals(90.0, stats.getSuccessRate24h(), 0.01);
    }

    @Test
    @DisplayName("getWidgetStats with zero executions returns 100% success rate")
    void getWidgetStats_zeroExecutions_returns100PercentSuccess() {
        when(actionRepository.countByWorkspaceIdAndEnabledTrue(WORKSPACE_ID)).thenReturn(3L);
        when(executionLogRepository.countByWorkspaceIdAndExecutedAtAfter(eq(WORKSPACE_ID), any(Instant.class)))
                .thenReturn(0L);
        when(executionLogRepository.countByWorkspaceIdAndSuccessAndExecutedAtAfter(
                eq(WORKSPACE_ID), eq(false), any(Instant.class)))
                .thenReturn(0L);

        WidgetStats stats = logService.getWidgetStats(WORKSPACE_ID);

        assertEquals(3, stats.getActiveActionCount());
        assertEquals(0, stats.getTotalExecutions24h());
        assertEquals(0, stats.getFailedExecutions24h());
        assertEquals(100.0, stats.getSuccessRate24h(), 0.01);
    }

    @Test
    @DisplayName("getWidgetStats with all failures returns 0% success rate")
    void getWidgetStats_allFailures_returnsZeroPercent() {
        when(actionRepository.countByWorkspaceIdAndEnabledTrue(WORKSPACE_ID)).thenReturn(2L);
        when(executionLogRepository.countByWorkspaceIdAndExecutedAtAfter(eq(WORKSPACE_ID), any(Instant.class)))
                .thenReturn(50L);
        when(executionLogRepository.countByWorkspaceIdAndSuccessAndExecutedAtAfter(
                eq(WORKSPACE_ID), eq(false), any(Instant.class)))
                .thenReturn(50L);

        WidgetStats stats = logService.getWidgetStats(WORKSPACE_ID);

        assertEquals(0.0, stats.getSuccessRate24h(), 0.01);
    }

    // ── Helpers ──

    private ExecutionLog createTestLog(UUID actionId, int status, boolean success) {
        ExecutionLog log = new ExecutionLog();
        log.setId((long) (Math.random() * 10000));
        log.setActionId(actionId);
        log.setWorkspaceId(WORKSPACE_ID);
        log.setEventType("NEW_TIME_ENTRY");
        log.setRequestUrl("https://example.com/hook");
        log.setRequestMethod("POST");
        log.setResponseStatus(status);
        log.setResponseTimeMs(42);
        log.setResponseBody("{\"ok\":true}");
        log.setSuccess(success);
        log.setExecutedAt(Instant.now());
        return log;
    }
}
