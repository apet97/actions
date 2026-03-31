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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
        JsonObject payload = parsePayload(body, "INSTALLED");

        if (!payload.has("workspaceId") || !payload.has("addonId") || !payload.has("authToken")) {
            throw new IllegalArgumentException(
                    "INSTALLED payload missing required fields: workspaceId, addonId, or authToken");
        }

        String workspaceId = payload.get("workspaceId").getAsString();
        String addonId = payload.get("addonId").getAsString();
        String authToken = payload.get("authToken").getAsString();
        String apiUrl = payload.has("apiUrl") ? payload.get("apiUrl").getAsString() : "";
        String addonUserId = payload.has("addonUserId") ? payload.get("addonUserId").getAsString() : null;

        MDC.put("workspaceId", workspaceId);
        MDC.put("addonId", addonId);

        try {
            Installation installation = installationRepository.findById(workspaceId)
                    .orElse(new Installation());
            installation.setWorkspaceId(workspaceId);
            installation.setAddonId(addonId);
            installation.setAuthTokenEnc(tokenService.encrypt(authToken));
            installation.setApiUrl(normalizeApiUrl(apiUrl));
            installation.setAddonUserId(addonUserId);
            installation.setStatus(InstallationStatus.ACTIVE);
            installationRepository.save(installation);

            if (payload.has("webhooks")) {
                JsonArray webhooks = payload.getAsJsonArray("webhooks");
                Map<String, WebhookToken> existingByPath = new LinkedHashMap<>();
                for (WebhookToken token : webhookTokenRepository.findAllByWorkspaceId(workspaceId)) {
                    existingByPath.put(token.getWebhookPath(), token);
                }

                List<WebhookToken> tokensToSave = new ArrayList<>();
                for (JsonElement elem : webhooks) {
                    JsonObject wh = elem.getAsJsonObject();
                    if (!wh.has("path") || !wh.has("authToken")
                            || wh.get("path").isJsonNull() || wh.get("authToken").isJsonNull()) {
                        log.warn("Skipping webhook with missing path or authToken in INSTALLED payload");
                        continue;
                    }

                    String path = normalizeWebhookPath(wh.get("path").getAsString());
                    if (path == null) {
                        log.warn("Skipping webhook with invalid path in INSTALLED payload");
                        continue;
                    }

                    WebhookToken webhookToken = existingByPath.getOrDefault(path, new WebhookToken());
                    webhookToken.setWorkspaceId(workspaceId);
                    webhookToken.setWebhookPath(path);
                    webhookToken.setAuthTokenEnc(tokenService.encrypt(wh.get("authToken").getAsString()));
                    tokensToSave.add(webhookToken);
                }

                if (!tokensToSave.isEmpty()) {
                    webhookTokenRepository.saveAll(tokensToSave);
                }
            }

            log.info("Addon installed for workspace {}", workspaceId);
        } finally {
            clearLifecycleMdc();
        }
    }

    @Transactional
    public void handleDeleted(String body) {
        JsonObject payload = parsePayload(body, "DELETED");

        if (!payload.has("workspaceId")) {
            throw new IllegalArgumentException("DELETED payload missing workspaceId");
        }
        String workspaceId = payload.get("workspaceId").getAsString();

        MDC.put("workspaceId", workspaceId);

        try {
            installationRepository.deleteById(workspaceId);
            log.info("Addon deleted for workspace {}, all data cleaned up", workspaceId);
        } finally {
            clearLifecycleMdc();
        }
    }

    @Transactional
    public void handleStatusChanged(String body) {
        JsonObject payload = parsePayload(body, "STATUS_CHANGED");
        if (!payload.has("workspaceId") || !payload.has("status")) {
            throw new IllegalArgumentException("STATUS_CHANGED payload missing required fields: workspaceId or status");
        }
        String workspaceId = payload.get("workspaceId").getAsString();
        String status = payload.get("status").getAsString();

        MDC.put("workspaceId", workspaceId);

        try {
            installationRepository.findById(workspaceId).ifPresent(installation -> {
                try {
                    installation.setStatus(InstallationStatus.valueOf(status));
                } catch (IllegalArgumentException e) {
                    throw new IllegalArgumentException("Unknown installation status: " + status);
                }
                installationRepository.save(installation);
                log.info("Addon status changed to {} for workspace {}", status, workspaceId);
            });
        } finally {
            clearLifecycleMdc();
        }
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

    private String normalizeApiUrl(String apiUrl) {
        if (apiUrl == null || apiUrl.isBlank()) {
            return "";
        }
        try {
            return ClockifyUrlNormalizer.normalizeBackendApiUrl(apiUrl);
        } catch (IllegalArgumentException e) {
            log.warn("Failed to normalize apiUrl '{}': {}", apiUrl, e.getMessage());
            return apiUrl;
        }
    }

    private String normalizeWebhookPath(String rawPath) {
        if (rawPath == null || rawPath.isBlank()) {
            return null;
        }
        String path = rawPath.trim();
        try {
            if (path.startsWith("http://") || path.startsWith("https://")) {
                path = java.net.URI.create(path).getPath();
            }
        } catch (Exception e) {
            return null;
        }
        if (path == null || path.isBlank()) {
            return null;
        }
        return path.replaceAll("^/+", "/");
    }

    private void clearLifecycleMdc() {
        MDC.remove("workspaceId");
        MDC.remove("addonId");
    }

    private JsonObject parsePayload(String body, String lifecycleType) {
        try {
            JsonObject payload = GsonProvider.get().fromJson(body, JsonObject.class);
            if (payload == null) {
                throw new IllegalArgumentException(lifecycleType + " payload must be a JSON object");
            }
            return payload;
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("Malformed " + lifecycleType + " payload", e);
        }
    }
}
