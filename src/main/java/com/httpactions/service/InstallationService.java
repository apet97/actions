package com.httpactions.service;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.httpactions.config.GsonProvider;
import com.httpactions.model.entity.Installation;
import com.httpactions.model.entity.WebhookToken;
import com.httpactions.model.enums.InstallationStatus;
import com.httpactions.repository.InstallationRepository;
import com.httpactions.repository.WebhookTokenRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
public class InstallationService {

    private static final Logger log = LoggerFactory.getLogger(InstallationService.class);

    private final InstallationRepository installationRepository;
    private final WebhookTokenRepository webhookTokenRepository;
    private final TokenService tokenService;

    public InstallationService(InstallationRepository installationRepository,
                               WebhookTokenRepository webhookTokenRepository,
                               TokenService tokenService) {
        this.installationRepository = installationRepository;
        this.webhookTokenRepository = webhookTokenRepository;
        this.tokenService = tokenService;
    }

    @Transactional
    public void handleInstalled(String body) {
        JsonObject payload = GsonProvider.get().fromJson(body, JsonObject.class);

        // H2: null-check required fields
        if (!payload.has("workspaceId") || !payload.has("addonId") || !payload.has("authToken")) {
            log.error("INSTALLED payload missing required fields: workspaceId, addonId, or authToken");
            MDC.clear(); // L6
            return;
        }

        String workspaceId = payload.get("workspaceId").getAsString();
        String addonId = payload.get("addonId").getAsString();
        String authToken = payload.get("authToken").getAsString();
        String apiUrl = payload.has("apiUrl") ? payload.get("apiUrl").getAsString() : "";
        String addonUserId = payload.has("addonUserId") ? payload.get("addonUserId").getAsString() : null;

        MDC.put("workspaceId", workspaceId);
        MDC.put("addonId", addonId);

        // Store installation (upsert for re-installs)
        Installation installation = installationRepository.findById(workspaceId)
                .orElse(new Installation());
        installation.setWorkspaceId(workspaceId);
        installation.setAddonId(addonId);
        installation.setAuthTokenEnc(tokenService.encrypt(authToken));
        installation.setApiUrl(apiUrl);
        installation.setAddonUserId(addonUserId);
        installation.setStatus(InstallationStatus.ACTIVE);
        installationRepository.save(installation);

        // Store per-webhook auth tokens
        if (payload.has("webhooks")) {
            JsonArray webhooks = payload.getAsJsonArray("webhooks");
            for (JsonElement elem : webhooks) {
                JsonObject wh = elem.getAsJsonObject();
                String path = wh.get("path").getAsString();
                String whAuthToken = wh.get("authToken").getAsString();

                WebhookToken webhookToken = webhookTokenRepository
                        .findByWorkspaceIdAndWebhookPath(workspaceId, path)
                        .orElse(new WebhookToken());
                webhookToken.setWorkspaceId(workspaceId);
                webhookToken.setWebhookPath(path);
                webhookToken.setAuthTokenEnc(tokenService.encrypt(whAuthToken));
                webhookTokenRepository.save(webhookToken);
            }
        }

        log.info("Addon installed for workspace {}", workspaceId);
        MDC.clear();
    }

    @Transactional
    public void handleDeleted(String body) {
        JsonObject payload = GsonProvider.get().fromJson(body, JsonObject.class);

        // H3: null-check workspaceId
        if (!payload.has("workspaceId")) {
            log.error("DELETED payload missing workspaceId");
            MDC.clear(); // L6
            return;
        }
        String workspaceId = payload.get("workspaceId").getAsString();

        MDC.put("workspaceId", workspaceId);

        // Cascade deletes webhook_tokens, actions, execution_logs via FK constraints
        installationRepository.deleteById(workspaceId);

        log.info("Addon deleted for workspace {}, all data cleaned up", workspaceId);
        MDC.clear();
    }

    @Transactional
    public void handleStatusChanged(String body) {
        JsonObject payload = GsonProvider.get().fromJson(body, JsonObject.class);
        if (payload == null || !payload.has("workspaceId") || !payload.has("status")) {
            log.error("STATUS_CHANGED payload missing required fields: workspaceId or status");
            MDC.clear();
            return;
        }
        String workspaceId = payload.get("workspaceId").getAsString();
        String status = payload.get("status").getAsString();

        MDC.put("workspaceId", workspaceId);

        installationRepository.findById(workspaceId).ifPresent(installation -> {
            // H1: handle unknown status gracefully
            try {
                installation.setStatus(InstallationStatus.valueOf(status));
            } catch (IllegalArgumentException e) {
                log.warn("Unknown installation status '{}' for workspace {}, ignoring", status, workspaceId);
                return;
            }
            installationRepository.save(installation);
            log.info("Addon status changed to {} for workspace {}", status, workspaceId);
        });

        MDC.clear();
    }

    public void handleSettingsUpdated(String body) {
        JsonObject payload = GsonProvider.get().fromJson(body, JsonObject.class);
        String workspaceId = payload != null && payload.has("workspaceId")
                ? payload.get("workspaceId").getAsString()
                : "unknown";
        log.info("Settings updated for workspace {} (no-op in P0)", workspaceId);
    }

    public Optional<Installation> getActiveInstallation(String workspaceId) {
        return installationRepository.findById(workspaceId)
                .filter(i -> i.getStatus() == InstallationStatus.ACTIVE);
    }
}
