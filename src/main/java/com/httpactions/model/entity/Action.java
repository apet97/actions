package com.httpactions.model.entity;

import com.httpactions.model.dto.ExecutionCondition;
import com.httpactions.model.dto.SuccessCondition;
import com.httpactions.model.enums.EventType;
import com.httpactions.model.enums.HttpMethod;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "actions")
public class Action {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "workspace_id", nullable = false, length = 64)
    private String workspaceId;

    @Column(name = "name", nullable = false, length = 128)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 64)
    private EventType eventType;

    @Column(name = "enabled", nullable = false)
    private boolean enabled = true;

    @Enumerated(EnumType.STRING)
    @Column(name = "http_method", length = 8)
    private HttpMethod httpMethod;

    @Column(name = "url_template", columnDefinition = "TEXT")
    private String urlTemplate;

    @Column(name = "headers_enc", columnDefinition = "TEXT")
    private String headersEnc;

    @Column(name = "body_template", columnDefinition = "TEXT")
    private String bodyTemplate;

    @Column(name = "signing_secret_enc", columnDefinition = "TEXT")
    private String signingSecretEnc;

    @Column(name = "retry_count", nullable = false)
    private int retryCount = 3;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "success_conditions", columnDefinition = "jsonb")
    private List<SuccessCondition> successConditions;

    @Column(name = "chain_order")
    private Integer chainOrder;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "conditions", columnDefinition = "jsonb")
    private List<ExecutionCondition> executionConditions;

    @Column(name = "cron_expression", length = 64)
    private String cronExpression;

    @Column(name = "last_scheduled_run")
    private Instant lastScheduledRun;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public Action() {}

    @PrePersist
    public void onCreate() {
        Instant now = Instant.now();
        if (this.createdAt == null) {
            this.createdAt = now;
        }
        this.updatedAt = now;
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public String getWorkspaceId() { return workspaceId; }
    public void setWorkspaceId(String workspaceId) { this.workspaceId = workspaceId; }

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

    public String getHeadersEnc() { return headersEnc; }
    public void setHeadersEnc(String headersEnc) { this.headersEnc = headersEnc; }

    public String getBodyTemplate() { return bodyTemplate; }
    public void setBodyTemplate(String bodyTemplate) { this.bodyTemplate = bodyTemplate; }

    public String getSigningSecretEnc() { return signingSecretEnc; }
    public void setSigningSecretEnc(String signingSecretEnc) { this.signingSecretEnc = signingSecretEnc; }

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

    public Instant getLastScheduledRun() { return lastScheduledRun; }
    public void setLastScheduledRun(Instant lastScheduledRun) { this.lastScheduledRun = lastScheduledRun; }

    @PreUpdate
    public void onUpdate() {
        this.updatedAt = Instant.now();
    }

    @Override
    public final boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Action action = (Action) o;
        return id != null && Objects.equals(id, action.id);
    }

    @Override
    public final int hashCode() {
        return getClass().hashCode();
    }
}
