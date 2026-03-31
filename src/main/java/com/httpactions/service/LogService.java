package com.httpactions.service;

import com.httpactions.model.dto.ExecutionLogResponse;
import com.httpactions.model.dto.WidgetStats;
import com.httpactions.model.dto.WidgetStatsRow;
import com.httpactions.model.entity.ExecutionLog;
import com.httpactions.repository.ActionRepository;
import com.httpactions.repository.ExecutionLogRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Service
public class LogService {

    private final ExecutionLogRepository executionLogRepository;
    private final ActionRepository actionRepository;

    public LogService(ExecutionLogRepository executionLogRepository, ActionRepository actionRepository) {
        this.executionLogRepository = executionLogRepository;
        this.actionRepository = actionRepository;
    }

    public Page<ExecutionLogResponse> getLogsForAction(UUID actionId, int page, int size) {
        return executionLogRepository.findByActionIdOrderByExecutedAtDesc(actionId, PageRequest.of(page, size))
                .map(this::toResponse);
    }

    public Page<ExecutionLogResponse> getLogsForWorkspace(String workspaceId, int page, int size) {
        return executionLogRepository.findByWorkspaceIdOrderByExecutedAtDesc(workspaceId, PageRequest.of(page, size))
                .map(this::toResponse);
    }

    /**
     * Aggregate statistics for the widget dashboard.
     * Execution stats fetched in a single query to ensure consistency.
     */
    public WidgetStats getWidgetStats(String workspaceId) {
        Instant since = Instant.now().minus(24, ChronoUnit.HOURS);

        long activeActionCount = actionRepository.countByWorkspaceIdAndEnabledTrue(workspaceId);

        WidgetStatsRow stats = executionLogRepository.countWidgetStats(workspaceId, since);
        long totalExecutions24h = stats != null ? stats.total() : 0L;
        long failedExecutions24h = stats != null ? stats.failed() : 0L;
        double successRate24h = totalExecutions24h > 0
                ? ((double) (totalExecutions24h - failedExecutions24h) / totalExecutions24h) * 100.0
                : 100.0;

        return new WidgetStats(activeActionCount, totalExecutions24h, failedExecutions24h, successRate24h);
    }

    private ExecutionLogResponse toResponse(ExecutionLog entity) {
        ExecutionLogResponse response = new ExecutionLogResponse();
        response.setId(entity.getId());
        response.setActionId(entity.getActionId());
        response.setEventType(entity.getEventType());
        response.setRequestUrl(entity.getRequestUrl());
        response.setRequestMethod(entity.getRequestMethod());
        response.setResponseStatus(entity.getResponseStatus());
        response.setResponseTimeMs(entity.getResponseTimeMs());
        response.setResponseBody(entity.getResponseBody());
        response.setErrorMessage(entity.getErrorMessage());
        response.setSuccess(entity.isSuccess());
        response.setExecutedAt(entity.getExecutedAt());
        return response;
    }
}
