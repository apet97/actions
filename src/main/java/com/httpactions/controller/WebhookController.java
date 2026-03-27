package com.httpactions.controller;

import com.httpactions.config.GsonProvider;
import com.httpactions.model.entity.Installation;
import com.httpactions.model.entity.WebhookToken;
import com.httpactions.model.enums.EventType;
import com.httpactions.repository.WebhookEventRepository;
import com.httpactions.repository.WebhookTokenRepository;
import com.httpactions.service.ExecutionService;
import com.httpactions.service.InstallationService;
import com.httpactions.service.TokenService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/webhook")
public class WebhookController {

    private static final Logger log = LoggerFactory.getLogger(WebhookController.class);

    private final TokenService tokenService;
    private final InstallationService installationService;
    private final ExecutionService executionService;
    private final WebhookEventRepository webhookEventRepository;
    private final WebhookTokenRepository webhookTokenRepository;

    public WebhookController(TokenService tokenService, InstallationService installationService,
                             ExecutionService executionService, WebhookEventRepository webhookEventRepository,
                             WebhookTokenRepository webhookTokenRepository) {
        this.tokenService = tokenService;
        this.installationService = installationService;
        this.executionService = executionService;
        this.webhookEventRepository = webhookEventRepository;
        this.webhookTokenRepository = webhookTokenRepository;
    }

    @PostMapping("/{eventSlug}")
    @Transactional
    public ResponseEntity<Void> handleWebhook(
            @PathVariable String eventSlug,
            @RequestHeader(value = "Clockify-Signature", required = false) String signature,
            @RequestHeader(value = "clockify-webhook-event-type", required = false) String webhookEventType,
            @RequestBody String body) {

        // M1: explicit check for required headers (returns meaningful error instead of Spring 400)
        if (signature == null || signature.isBlank() || webhookEventType == null || webhookEventType.isBlank()) {
            log.warn("Webhook missing required headers: Clockify-Signature or clockify-webhook-event-type");
            return ResponseEntity.status(401).build();
        }

        // Step 1: Verify signature JWT
        Map<String, Object> claims = tokenService.verifyAndParseClaims(signature);
        if (claims == null) {
            log.warn("Invalid webhook signature for slug {}", eventSlug);
            return ResponseEntity.status(401).build();
        }

        // Step 2: Extract workspace info
        String workspaceId = (String) claims.get("workspaceId");
        if (workspaceId == null) {
            log.warn("No workspaceId in webhook JWT claims");
            return ResponseEntity.status(401).build();
        }

        MDC.put("workspaceId", workspaceId);
        MDC.put("eventType", webhookEventType);

        try {
            // Step 3: Check installation is active
            Optional<Installation> installationOpt = installationService.getActiveInstallation(workspaceId);
            if (installationOpt.isEmpty()) {
                log.debug("Webhook received for inactive/unknown workspace {}", workspaceId);
                return ResponseEntity.ok().build(); // Accept but discard
            }
            Installation installation = installationOpt.get();
            Object jwtSubject = claims.get("sub");
            if (!(jwtSubject instanceof String subject) || !subject.equals(installation.getAddonId())) {
                log.warn("Webhook addonId mismatch for workspace {}", workspaceId);
                return ResponseEntity.status(401).build();
            }

            // Step 4: Resolve event type
            EventType eventType;
            try {
                eventType = EventType.fromWebhookHeader(webhookEventType);
            } catch (IllegalArgumentException e) {
                log.warn("Unknown webhook event type: {}", webhookEventType);
                return ResponseEntity.ok().build(); // Accept but discard unknown events
            }

            // Step 4.5: Verify slug matches header (defense-in-depth)
            if (!eventType.getSlug().equals(eventSlug)) {
                log.warn("Slug/header mismatch: path={}, header={}", eventSlug, webhookEventType);
                return ResponseEntity.ok().build();
            }

            // Step 4.6: Verify per-webhook auth token (BEFORE idempotency — C3 fix)
            Optional<WebhookToken> storedToken = webhookTokenRepository
                    .findByWorkspaceIdAndWebhookPath(workspaceId, "/webhook/" + eventSlug);
            if (storedToken.isPresent()) {
                String expectedToken = tokenService.decrypt(storedToken.get().getAuthTokenEnc());
                // C2 fix: treat null decrypt of non-null ciphertext as verification failure
                if (storedToken.get().getAuthTokenEnc() != null && expectedToken == null) {
                    log.error("Failed to decrypt webhook auth token for workspace {}", workspaceId);
                    return ResponseEntity.status(401).build();
                }
                String actualToken = (String) claims.get("authToken");
                if (expectedToken != null && !expectedToken.equals(actualToken)) {
                    log.warn("Webhook authToken mismatch for workspace {} slug {}", workspaceId, eventSlug);
                    return ResponseEntity.status(401).build();
                }
            } else {
                log.debug("No stored webhook token for workspace {} slug {} — legacy install, allowing", workspaceId, eventSlug);
            }

            // Step 4.7: Idempotency — atomic insert-if-absent (H6+H7 fix)
            String eventId = extractEventId(body);
            int inserted = webhookEventRepository.insertIfAbsent(workspaceId, eventId, eventType.name());
            if (inserted == 0) {
                log.debug("Duplicate webhook event {} for workspace {}, skipping", eventId, workspaceId);
                return ResponseEntity.ok().build();
            }

            // Step 5: Return 200 immediately, process asynchronously AFTER transaction commits
            // (idempotency insert must be visible to other instances before async processing starts)
            if (TransactionSynchronizationManager.isSynchronizationActive()) {
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        executionService.processWebhookAsync(workspaceId, eventType, body);
                    }
                });
            } else {
                executionService.processWebhookAsync(workspaceId, eventType, body);
            }

            return ResponseEntity.ok().build();
        } finally {
            MDC.clear();
        }
    }

    private String extractEventId(String body) {
        try {
            com.google.gson.JsonObject json = GsonProvider.get().fromJson(body, com.google.gson.JsonObject.class);
            if (json.has("id") && json.get("id").isJsonPrimitive()) {
                return json.get("id").getAsString();
            }
        } catch (Exception e) {
            // fall through to hash
        }
        // Fallback: SHA-256 hash of body
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(body.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            return String.valueOf(body.hashCode());
        }
    }
}
