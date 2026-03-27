package com.httpactions.model.dto;

import com.httpactions.model.enums.EventType;
import com.httpactions.model.enums.HttpMethod;

import java.util.List;

/**
 * DTO for bulk export/import of actions.
 * Excludes id, workspaceId, timestamps, and encrypted fields (secrets).
 */
public class ExportAction {

    private String name;
    private EventType eventType;
    private HttpMethod httpMethod;
    private String urlTemplate;
    private String bodyTemplate;
    private int retryCount;
    private boolean enabled;
    private List<SuccessCondition> successConditions;
    private Integer chainOrder;
    private List<ExecutionCondition> executionConditions;
    private String cronExpression;

    public ExportAction() {}

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public EventType getEventType() { return eventType; }
    public void setEventType(EventType eventType) { this.eventType = eventType; }

    public HttpMethod getHttpMethod() { return httpMethod; }
    public void setHttpMethod(HttpMethod httpMethod) { this.httpMethod = httpMethod; }

    public String getUrlTemplate() { return urlTemplate; }
    public void setUrlTemplate(String urlTemplate) { this.urlTemplate = urlTemplate; }

    public String getBodyTemplate() { return bodyTemplate; }
    public void setBodyTemplate(String bodyTemplate) { this.bodyTemplate = bodyTemplate; }

    public int getRetryCount() { return retryCount; }
    public void setRetryCount(int retryCount) { this.retryCount = retryCount; }

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public List<SuccessCondition> getSuccessConditions() { return successConditions; }
    public void setSuccessConditions(List<SuccessCondition> successConditions) { this.successConditions = successConditions; }

    public Integer getChainOrder() { return chainOrder; }
    public void setChainOrder(Integer chainOrder) { this.chainOrder = chainOrder; }

    public List<ExecutionCondition> getExecutionConditions() { return executionConditions; }
    public void setExecutionConditions(List<ExecutionCondition> executionConditions) { this.executionConditions = executionConditions; }

    public String getCronExpression() { return cronExpression; }
    public void setCronExpression(String cronExpression) { this.cronExpression = cronExpression; }
}
