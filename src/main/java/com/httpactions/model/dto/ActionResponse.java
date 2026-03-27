package com.httpactions.model.dto;

import com.httpactions.model.enums.EventType;
import com.httpactions.model.enums.HttpMethod;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class ActionResponse {

    private UUID id;
    private String name;
    private EventType eventType;
    private boolean enabled;
    private HttpMethod httpMethod;
    private String urlTemplate;
    private Map<String, String> headers;
    private String bodyTemplate;
    private boolean hasSigning;
    private int retryCount;
    private Instant createdAt;
    private Instant updatedAt;
    private List<SuccessCondition> successConditions;
    private Integer chainOrder;
    private List<ExecutionCondition> executionConditions;
    private String cronExpression;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public EventType getEventType() { return eventType; }
    public void setEventType(EventType eventType) { this.eventType = eventType; }

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public HttpMethod getHttpMethod() { return httpMethod; }
    public void setHttpMethod(HttpMethod httpMethod) { this.httpMethod = httpMethod; }

    public String getUrlTemplate() { return urlTemplate; }
    public void setUrlTemplate(String urlTemplate) { this.urlTemplate = urlTemplate; }

    public Map<String, String> getHeaders() { return headers; }
    public void setHeaders(Map<String, String> headers) { this.headers = headers; }

    public String getBodyTemplate() { return bodyTemplate; }
    public void setBodyTemplate(String bodyTemplate) { this.bodyTemplate = bodyTemplate; }

    public boolean isHasSigning() { return hasSigning; }
    public void setHasSigning(boolean hasSigning) { this.hasSigning = hasSigning; }

    public int getRetryCount() { return retryCount; }
    public void setRetryCount(int retryCount) { this.retryCount = retryCount; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }

    public List<SuccessCondition> getSuccessConditions() { return successConditions; }
    public void setSuccessConditions(List<SuccessCondition> successConditions) { this.successConditions = successConditions; }

    public Integer getChainOrder() { return chainOrder; }
    public void setChainOrder(Integer chainOrder) { this.chainOrder = chainOrder; }

    public List<ExecutionCondition> getExecutionConditions() { return executionConditions; }
    public void setExecutionConditions(List<ExecutionCondition> executionConditions) { this.executionConditions = executionConditions; }

    public String getCronExpression() { return cronExpression; }
    public void setCronExpression(String cronExpression) { this.cronExpression = cronExpression; }
}
