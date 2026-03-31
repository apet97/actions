package com.httpactions.service;

import com.httpactions.controller.UnauthorizedAddonException;
import com.httpactions.model.entity.Installation;
import com.httpactions.model.entity.WebhookToken;
import com.httpactions.model.enums.EventType;
import com.httpactions.repository.WebhookEventRepository;
import com.httpactions.repository.WebhookTokenRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;
import java.util.Optional;

@Service
public class WebhookRequestService {

    private static final Logger log = LoggerFactory.getLogger(WebhookRequestService.class);

    private final TokenService tokenService;
    private final InstallationService installationService;
    private final WebhookEventRepository webhookEventRepository;
    private final WebhookTokenRepository webhookTokenRepository;
    private final MeterRegistry meterRegistry;

    public WebhookRequestService(TokenService tokenService,
                                 InstallationService installationService,
                                 WebhookEventRepository webhookEventRepository,
                                 WebhookTokenRepository webhookTokenRepository,
                                 MeterRegistry meterRegistry) {
        this.tokenService = tokenService;
        this.installationService = installationService;
        this.webhookEventRepository = webhookEventRepository;
        this.webhookTokenRepository = webhookTokenRepository;
        this.meterRegistry = meterRegistry;
    }

    public Optional<ProcessableWebhookEvent> resolve(String eventSlug,
                                                     String signature,
                                                     String webhookEventType,
                                                     String body) {
        if (signature == null || signature.isBlank() || webhookEventType == null || webhookEventType.isBlank()) {
            throw new UnauthorizedAddonException("Missing required webhook headers");
        }

        Map<String, Object> claims = tokenService.verifyAndParseClaims(signature);
        if (claims == null) {
            throw new UnauthorizedAddonException("Invalid webhook signature");
        }

        String workspaceId = claims.get("workspaceId") instanceof String value ? value : null;
        if (workspaceId == null || workspaceId.isBlank()) {
            throw new UnauthorizedAddonException("Invalid webhook signature");
        }

        Optional<Installation> installationOpt = installationService.getActiveInstallation(workspaceId);
        if (installationOpt.isEmpty()) {
            log.debug("Webhook received for inactive/unknown workspace {}", workspaceId);
            return Optional.empty();
        }

        Installation installation = installationOpt.get();
        String jwtAddonId = claims.get("addonId") instanceof String value ? value : null;
        if (jwtAddonId != null && !jwtAddonId.equals(installation.getAddonId())) {
            log.warn("Webhook addonId mismatch for workspace {}: jwt={} stored={}",
                    workspaceId, jwtAddonId, installation.getAddonId());
            throw new UnauthorizedAddonException("Webhook addonId mismatch");
        }

        EventType eventType;
        try {
            eventType = EventType.fromWebhookHeader(webhookEventType);
        } catch (IllegalArgumentException e) {
            log.warn("Unknown webhook event type: {}", webhookEventType);
            return Optional.empty();
        }

        if (!eventType.getSlug().equals(eventSlug)) {
            log.warn("Slug/header mismatch: path={}, header={}", eventSlug, webhookEventType);
            return Optional.empty();
        }

        verifyWebhookToken(workspaceId, eventSlug, claims);

        String eventId = extractEventId(body, eventType.name());
        int inserted = webhookEventRepository.insertIfAbsent(workspaceId, eventId, eventType.name());
        if (inserted == 0) {
            log.debug("Duplicate webhook event {} for workspace {}, skipping", eventId, workspaceId);
            return Optional.empty();
        }

        return Optional.of(new ProcessableWebhookEvent(workspaceId, eventType, body));
    }

    private void verifyWebhookToken(String workspaceId, String eventSlug, Map<String, Object> claims) {
        Optional<WebhookToken> storedToken = webhookTokenRepository
                .findByWorkspaceIdAndWebhookPath(workspaceId, "/webhook/" + eventSlug);
        if (storedToken.isEmpty()) {
            log.info("No stored webhook token for workspace {} slug {} - legacy install, allowing", workspaceId, eventSlug);
            Counter.builder("clockify.webhook.legacy_auth")
                    .register(meterRegistry)
                    .increment();
            return;
        }

        WebhookToken token = storedToken.get();
        String expectedToken = tokenService.decrypt(token.getAuthTokenEnc());
        if (token.getAuthTokenEnc() != null && expectedToken == null) {
            log.error("Failed to decrypt webhook auth token for workspace {}", workspaceId);
            throw new UnauthorizedAddonException("Webhook auth token verification failed");
        }

        String actualToken = claims.get("authToken") instanceof String value ? value : null;
        if (expectedToken != null && !expectedToken.equals(actualToken)) {
            log.warn("Webhook authToken mismatch for workspace {} slug {}", workspaceId, eventSlug);
            throw new UnauthorizedAddonException("Webhook auth token mismatch");
        }
    }

    private String extractEventId(String body, String eventType) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(eventType.getBytes(StandardCharsets.UTF_8));
            digest.update((byte) ':');
            byte[] hash = digest.digest(body.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable for webhook idempotency", e);
        }
    }

    public record ProcessableWebhookEvent(String workspaceId, EventType eventType, String body) {
    }
}
