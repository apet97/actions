package com.httpactions.model.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.Objects;

@Entity
@Table(name = "webhook_tokens", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"workspace_id", "webhook_path"})
})
public class WebhookToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "workspace_id", nullable = false, length = 64)
    private String workspaceId;

    @Column(name = "webhook_path", nullable = false, length = 256)
    private String webhookPath;

    @Column(name = "auth_token_enc", nullable = false, columnDefinition = "TEXT")
    private String authTokenEnc;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public WebhookToken() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getWorkspaceId() { return workspaceId; }
    public void setWorkspaceId(String workspaceId) { this.workspaceId = workspaceId; }

    public String getWebhookPath() { return webhookPath; }
    public void setWebhookPath(String webhookPath) { this.webhookPath = webhookPath; }

    public String getAuthTokenEnc() { return authTokenEnc; }
    public void setAuthTokenEnc(String authTokenEnc) { this.authTokenEnc = authTokenEnc; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }

    @PrePersist
    public void onCreate() {
        Instant now = Instant.now();
        if (createdAt == null) {
            createdAt = now;
        }
        updatedAt = now;
    }

    @PreUpdate
    public void onUpdate() {
        updatedAt = Instant.now();
    }

    @Override
    public final boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        WebhookToken that = (WebhookToken) o;
        return id != null && Objects.equals(id, that.id);
    }

    @Override
    public final int hashCode() {
        return getClass().hashCode();
    }
}
