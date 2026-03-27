package com.httpactions.model.entity;

import com.httpactions.model.enums.InstallationStatus;
import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "installations")
public class Installation {

    @Id
    @Column(name = "workspace_id", length = 64)
    private String workspaceId;

    @Column(name = "addon_id", nullable = false, length = 64)
    private String addonId;

    @Column(name = "auth_token_enc", nullable = false, columnDefinition = "TEXT")
    private String authTokenEnc;

    @Column(name = "api_url", nullable = false, length = 512)
    private String apiUrl;

    @Column(name = "addon_user_id", length = 64)
    private String addonUserId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private InstallationStatus status = InstallationStatus.ACTIVE;

    @Column(name = "installed_at", nullable = false)
    private Instant installedAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public Installation() {}

    public String getWorkspaceId() { return workspaceId; }
    public void setWorkspaceId(String workspaceId) { this.workspaceId = workspaceId; }

    public String getAddonId() { return addonId; }
    public void setAddonId(String addonId) { this.addonId = addonId; }

    public String getAuthTokenEnc() { return authTokenEnc; }
    public void setAuthTokenEnc(String authTokenEnc) { this.authTokenEnc = authTokenEnc; }

    public String getApiUrl() { return apiUrl; }
    public void setApiUrl(String apiUrl) { this.apiUrl = apiUrl; }

    public String getAddonUserId() { return addonUserId; }
    public void setAddonUserId(String addonUserId) { this.addonUserId = addonUserId; }

    public InstallationStatus getStatus() { return status; }
    public void setStatus(InstallationStatus status) { this.status = status; }

    public Instant getInstalledAt() { return installedAt; }
    public void setInstalledAt(Instant installedAt) { this.installedAt = installedAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }

    @PrePersist
    public void onCreate() {
        Instant now = Instant.now();
        if (this.installedAt == null) {
            this.installedAt = now;
        }
        this.updatedAt = now;
    }

    @PreUpdate
    public void onUpdate() {
        this.updatedAt = Instant.now();
    }
}
