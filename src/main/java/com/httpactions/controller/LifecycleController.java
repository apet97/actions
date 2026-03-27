package com.httpactions.controller;

import com.httpactions.service.InstallationService;
import com.httpactions.service.TokenService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

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

        // H8: explicit 401 when token is missing (instead of Spring's default 400)
        if (lifecycleToken == null || lifecycleToken.isBlank()) {
            log.warn("Missing X-Addon-Lifecycle-Token on INSTALLED");
            return ResponseEntity.status(401).build();
        }

        Map<String, Object> claims = tokenService.verifyAndParseClaims(lifecycleToken);
        if (claims == null) {
            log.warn("Invalid lifecycle token on INSTALLED");
            return ResponseEntity.status(401).build();
        }

        installationService.handleInstalled(body);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/deleted")
    public ResponseEntity<Void> deleted(
            @RequestHeader(value = "X-Addon-Lifecycle-Token", required = false) String lifecycleToken,
            @RequestBody String body) {

        if (lifecycleToken == null || lifecycleToken.isBlank()) {
            log.warn("Missing X-Addon-Lifecycle-Token on DELETED");
            return ResponseEntity.status(401).build();
        }

        Map<String, Object> claims = tokenService.verifyAndParseClaims(lifecycleToken);
        if (claims == null) {
            log.warn("Invalid lifecycle token on DELETED");
            return ResponseEntity.status(401).build();
        }

        installationService.handleDeleted(body);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/status-changed")
    public ResponseEntity<Void> statusChanged(
            @RequestHeader(value = "X-Addon-Lifecycle-Token", required = false) String lifecycleToken,
            @RequestBody String body) {

        if (lifecycleToken == null || lifecycleToken.isBlank()) {
            log.warn("Missing X-Addon-Lifecycle-Token on STATUS_CHANGED");
            return ResponseEntity.status(401).build();
        }

        Map<String, Object> claims = tokenService.verifyAndParseClaims(lifecycleToken);
        if (claims == null) {
            log.warn("Invalid lifecycle token on STATUS_CHANGED");
            return ResponseEntity.status(401).build();
        }

        installationService.handleStatusChanged(body);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/settings-updated")
    public ResponseEntity<Void> settingsUpdated(
            @RequestHeader(value = "X-Addon-Lifecycle-Token", required = false) String lifecycleToken,
            @RequestBody String body) {

        if (lifecycleToken == null || lifecycleToken.isBlank()) {
            log.warn("Missing X-Addon-Lifecycle-Token on SETTINGS_UPDATED");
            return ResponseEntity.status(401).build();
        }

        Map<String, Object> claims = tokenService.verifyAndParseClaims(lifecycleToken);
        if (claims == null) {
            log.warn("Invalid lifecycle token on SETTINGS_UPDATED");
            return ResponseEntity.status(401).build();
        }

        installationService.handleSettingsUpdated(body);
        return ResponseEntity.ok().build();
    }
}
