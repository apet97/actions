package com.httpactions.model.entity;

import jakarta.persistence.*;

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

    public WebhookToken() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getWorkspaceId() { return workspaceId; }
    public void setWorkspaceId(String workspaceId) { this.workspaceId = workspaceId; }

    public String getWebhookPath() { return webhookPath; }
    public void setWebhookPath(String webhookPath) { this.webhookPath = webhookPath; }

    public String getAuthTokenEnc() { return authTokenEnc; }
    public void setAuthTokenEnc(String authTokenEnc) { this.authTokenEnc = authTokenEnc; }
}
