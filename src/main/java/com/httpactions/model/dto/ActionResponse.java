package com.httpactions.model.dto;

import com.httpactions.model.enums.EventType;
import com.httpactions.model.enums.HttpMethod;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record ActionResponse(
        UUID id,
        String name,
        EventType eventType,
        boolean enabled,
        HttpMethod httpMethod,
        String urlTemplate,
        Map<String, String> headers,
        String bodyTemplate,
        boolean hasSigning,
        int retryCount,
        Instant createdAt,
        Instant updatedAt,
        List<SuccessCondition> successConditions,
        Integer chainOrder,
        List<ExecutionCondition> executionConditions,
        String cronExpression
) {
    public UUID getId() { return id; }
    public String getName() { return name; }
    public EventType getEventType() { return eventType; }
    public boolean isEnabled() { return enabled; }
    public HttpMethod getHttpMethod() { return httpMethod; }
    public String getUrlTemplate() { return urlTemplate; }
    public Map<String, String> getHeaders() { return headers; }
    public String getBodyTemplate() { return bodyTemplate; }
    public boolean isHasSigning() { return hasSigning; }
    public int getRetryCount() { return retryCount; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public List<SuccessCondition> getSuccessConditions() { return successConditions; }
    public Integer getChainOrder() { return chainOrder; }
    public List<ExecutionCondition> getExecutionConditions() { return executionConditions; }
    public String getCronExpression() { return cronExpression; }
}
