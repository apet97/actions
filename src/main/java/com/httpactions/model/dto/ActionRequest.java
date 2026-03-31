package com.httpactions.model.dto;

import com.httpactions.model.enums.EventType;
import com.httpactions.model.enums.HttpMethod;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;
import java.util.Map;

public class ActionRequest {

    @NotBlank
    @Size(min = 1, max = 128)
    @Pattern(regexp = "^[A-Za-z0-9\\- ]+$", message = "Name may only contain letters, numbers, spaces, and dashes")
    private String name;

    @NotNull
    private EventType eventType;

    @NotNull
    private HttpMethod httpMethod;

    @NotBlank
    @Size(max = 2048)
    private String urlTemplate;

    @Size(max = 20)
    private Map<String, String> headers;

    @Size(max = 65536)
    private String bodyTemplate;

    private String signingSecret;

    @Min(0)
    @Max(10)
    private int retryCount = 3;

    private boolean enabled = true;

    @Valid
    @Size(max = 20)
    private List<SuccessCondition> successConditions;

    @Min(1)
    @Max(100)
    private Integer chainOrder;

    @Valid
    @Size(max = 20)
    private List<ExecutionCondition> executionConditions;
    private String cronExpression;

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public EventType getEventType() { return eventType; }
    public void setEventType(EventType eventType) { this.eventType = eventType; }

    public HttpMethod getHttpMethod() { return httpMethod; }
    public void setHttpMethod(HttpMethod httpMethod) { this.httpMethod = httpMethod; }

    public String getUrlTemplate() { return urlTemplate; }
    public void setUrlTemplate(String urlTemplate) { this.urlTemplate = urlTemplate; }

    public Map<String, String> getHeaders() { return headers; }
    public void setHeaders(Map<String, String> headers) { this.headers = headers; }

    public String getBodyTemplate() { return bodyTemplate; }
    public void setBodyTemplate(String bodyTemplate) { this.bodyTemplate = bodyTemplate; }

    public String getSigningSecret() { return signingSecret; }
    public void setSigningSecret(String signingSecret) { this.signingSecret = signingSecret; }

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

    @AssertTrue(message = "Header names must be 1-128 characters")
    public boolean isHeaderKeysValid() {
        if (headers == null) return true;
        return headers.keySet().stream().allMatch(key -> key != null && !key.isBlank() && key.length() <= 128);
    }

    @AssertTrue(message = "Header values must be 0-4096 characters")
    public boolean isHeaderValuesValid() {
        if (headers == null) return true;
        return headers.values().stream().allMatch(value -> value != null && value.length() <= 4096);
    }

    @AssertTrue(message = "Name must not start or end with spaces")
    public boolean isNameTrimmed() {
        return name == null || name.equals(name.trim());
    }

    @AssertTrue(message = "Duplicate header names are not allowed")
    public boolean isHeaderNamesUniqueIgnoreCase() {
        if (headers == null) return true;
        return headers.keySet().stream()
                .filter(key -> key != null)
                .map(key -> key.toLowerCase(java.util.Locale.ROOT))
                .distinct()
                .count() == headers.size();
    }
}
