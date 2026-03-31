package com.httpactions.controller;

import com.google.gson.JsonObject;
import com.httpactions.config.GsonProvider;
import com.httpactions.service.InstallationService;
import com.httpactions.service.TokenService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.function.Consumer;

@RestController
@RequestMapping("/lifecycle")
public class LifecycleController {

    private static final Logger log = LoggerFactory.getLogger(LifecycleController.class);

    private final InstallationService installationService;
    private final TokenService tokenService;

    public LifecycleController(InstallationService installationService, TokenService tokenService) {
        this.installationService = installationService;
        this.tokenService = tokenService;
    }

    @PostMapping("/installed")
    public ResponseEntity<Void> installed(
            @RequestHeader(value = "X-Addon-Lifecycle-Token", required = false) String lifecycleToken,
            @RequestBody String body) {
        return handleLifecycle(lifecycleToken, body, "INSTALLED", installationService::handleInstalled);
    }

    @PostMapping("/deleted")
    public ResponseEntity<Void> deleted(
            @RequestHeader(value = "X-Addon-Lifecycle-Token", required = false) String lifecycleToken,
            @RequestBody String body) {
        return handleLifecycle(lifecycleToken, body, "DELETED", installationService::handleDeleted);
    }

    @PostMapping("/status-changed")
    public ResponseEntity<Void> statusChanged(
            @RequestHeader(value = "X-Addon-Lifecycle-Token", required = false) String lifecycleToken,
            @RequestBody String body) {
        return handleLifecycle(lifecycleToken, body, "STATUS_CHANGED", installationService::handleStatusChanged);
    }

    @PostMapping("/settings-updated")
    public ResponseEntity<Void> settingsUpdated(
            @RequestHeader(value = "X-Addon-Lifecycle-Token", required = false) String lifecycleToken,
            @RequestBody String body) {
        return handleLifecycle(lifecycleToken, body, "SETTINGS_UPDATED", installationService::handleSettingsUpdated);
    }

    private ResponseEntity<Void> handleLifecycle(String lifecycleToken,
                                                 String body,
                                                 String lifecycleType,
                                                 Consumer<String> handler) {
        if (lifecycleToken == null || lifecycleToken.isBlank()) {
            log.warn("Missing X-Addon-Lifecycle-Token on {}", lifecycleType);
            return ResponseEntity.status(401).build();
        }

        Map<String, Object> claims = tokenService.verifyAndParseClaims(lifecycleToken);
        if (claims == null) {
            log.warn("Invalid lifecycle token on {}", lifecycleType);
            return ResponseEntity.status(401).build();
        }

        if (!workspaceMatches(body, claims)) {
            log.warn("Lifecycle workspace mismatch on {}", lifecycleType);
            return ResponseEntity.status(401).build();
        }

        handler.accept(body);
        return ResponseEntity.ok().build();
    }

    private boolean workspaceMatches(String body, Map<String, Object> claims) {
        String claimsWorkspaceId = claims.get("workspaceId") instanceof String value ? value : null;
        String bodyWorkspaceId = extractWorkspaceId(body);
        return claimsWorkspaceId != null
                && !claimsWorkspaceId.isBlank()
                && bodyWorkspaceId != null
                && !bodyWorkspaceId.isBlank()
                && claimsWorkspaceId.equals(bodyWorkspaceId);
    }

    private String extractWorkspaceId(String body) {
        try {
            JsonObject payload = GsonProvider.get().fromJson(body, JsonObject.class);
            if (payload != null && payload.has("workspaceId") && !payload.get("workspaceId").isJsonNull()) {
                return payload.get("workspaceId").getAsString();
            }
        } catch (Exception e) {
            log.debug("Failed to parse lifecycle payload for workspace alignment check: {}", e.getMessage());
        }
        return null;
    }
}
