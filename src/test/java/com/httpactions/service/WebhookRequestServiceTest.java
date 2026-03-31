package com.httpactions.service;

import com.httpactions.controller.UnauthorizedAddonException;
import com.httpactions.model.entity.Installation;
import com.httpactions.model.entity.WebhookToken;
import com.httpactions.model.enums.EventType;
import com.httpactions.repository.WebhookEventRepository;
import com.httpactions.repository.WebhookTokenRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WebhookRequestServiceTest {

    @Mock
    private TokenService tokenService;

    @Mock
    private InstallationService installationService;

    @Mock
    private WebhookEventRepository webhookEventRepository;

    @Mock
    private WebhookTokenRepository webhookTokenRepository;

    private SimpleMeterRegistry meterRegistry;
    private WebhookRequestService webhookRequestService;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        webhookRequestService = new WebhookRequestService(
                tokenService, installationService, webhookEventRepository, webhookTokenRepository, meterRegistry);
    }

    @Test
    void missingHeaders_throwsUnauthorized() {
        assertThrows(UnauthorizedAddonException.class,
                () -> webhookRequestService.resolve("new-time-entry", null, "NEW_TIME_ENTRY", "{}"));

        verifyNoInteractions(tokenService, installationService, webhookEventRepository, webhookTokenRepository);
    }

    @Test
    void validWebhook_resolvesAndInserts() {
        when(tokenService.verifyAndParseClaims("valid-jwt")).thenReturn(validClaims("expected-token"));
        when(installationService.getActiveInstallation("ws-1")).thenReturn(Optional.of(activeInstallation()));
        when(webhookTokenRepository.findByWorkspaceIdAndWebhookPath("ws-1", "/webhook/new-time-entry"))
                .thenReturn(Optional.empty());
        when(webhookEventRepository.insertIfAbsent(eq("ws-1"), any(), eq(EventType.NEW_TIME_ENTRY.name())))
                .thenReturn(1);

        Optional<WebhookRequestService.ProcessableWebhookEvent> resolved = webhookRequestService.resolve(
                "new-time-entry",
                "valid-jwt",
                "NEW_TIME_ENTRY",
                "{\"id\":\"evt-123\"}"
        );

        assertTrue(resolved.isPresent());
        assertEquals("ws-1", resolved.get().workspaceId());
        assertEquals(EventType.NEW_TIME_ENTRY, resolved.get().eventType());
        assertEquals(1.0, meterRegistry.get("clockify.webhook.legacy_auth").counter().count());
    }

    @Test
    void duplicateDelivery_returnsEmpty() {
        when(tokenService.verifyAndParseClaims("valid-jwt")).thenReturn(validClaims("expected-token"));
        when(installationService.getActiveInstallation("ws-1")).thenReturn(Optional.of(activeInstallation()));
        when(webhookTokenRepository.findByWorkspaceIdAndWebhookPath("ws-1", "/webhook/new-time-entry"))
                .thenReturn(Optional.empty());
        when(webhookEventRepository.insertIfAbsent(eq("ws-1"), any(), eq(EventType.NEW_TIME_ENTRY.name())))
                .thenReturn(1)
                .thenReturn(0);

        Optional<WebhookRequestService.ProcessableWebhookEvent> first = webhookRequestService.resolve(
                "new-time-entry", "valid-jwt", "NEW_TIME_ENTRY", "{\"id\":\"evt-123\"}");
        Optional<WebhookRequestService.ProcessableWebhookEvent> second = webhookRequestService.resolve(
                "new-time-entry", "valid-jwt", "NEW_TIME_ENTRY", "{\"id\":\"evt-123\"}");

        assertTrue(first.isPresent());
        assertTrue(second.isEmpty());
    }

    @Test
    void sameResourceIdDifferentPayload_isNotTreatedAsDuplicate() {
        when(tokenService.verifyAndParseClaims("valid-jwt")).thenReturn(validClaims("expected-token"));
        when(installationService.getActiveInstallation("ws-1")).thenReturn(Optional.of(activeInstallation()));
        when(webhookTokenRepository.findByWorkspaceIdAndWebhookPath("ws-1", "/webhook/new-time-entry"))
                .thenReturn(Optional.empty());
        when(webhookEventRepository.insertIfAbsent(eq("ws-1"), any(), eq(EventType.NEW_TIME_ENTRY.name())))
                .thenReturn(1);

        Optional<WebhookRequestService.ProcessableWebhookEvent> first = webhookRequestService.resolve(
                "new-time-entry", "valid-jwt", "NEW_TIME_ENTRY",
                "{\"id\":\"te-123\",\"description\":\"first\"}");
        Optional<WebhookRequestService.ProcessableWebhookEvent> second = webhookRequestService.resolve(
                "new-time-entry", "valid-jwt", "NEW_TIME_ENTRY",
                "{\"id\":\"te-123\",\"description\":\"second\"}");

        assertTrue(first.isPresent());
        assertTrue(second.isPresent());

        ArgumentCaptor<String> eventIdCaptor = ArgumentCaptor.forClass(String.class);
        verify(webhookEventRepository, times(2))
                .insertIfAbsent(eq("ws-1"), eventIdCaptor.capture(), eq(EventType.NEW_TIME_ENTRY.name()));
        assertEquals(2, eventIdCaptor.getAllValues().size());
        assertNotEquals(eventIdCaptor.getAllValues().get(0), eventIdCaptor.getAllValues().get(1));
    }

    @Test
    void decryptFailure_throwsUnauthorized() {
        WebhookToken storedToken = new WebhookToken();
        storedToken.setWorkspaceId("ws-1");
        storedToken.setWebhookPath("/webhook/new-time-entry");
        storedToken.setAuthTokenEnc("enc-token");

        when(tokenService.verifyAndParseClaims("valid-jwt")).thenReturn(validClaims("expected-token"));
        when(installationService.getActiveInstallation("ws-1")).thenReturn(Optional.of(activeInstallation()));
        when(webhookTokenRepository.findByWorkspaceIdAndWebhookPath("ws-1", "/webhook/new-time-entry"))
                .thenReturn(Optional.of(storedToken));
        when(tokenService.decrypt("enc-token")).thenReturn(null);

        assertThrows(UnauthorizedAddonException.class, () -> webhookRequestService.resolve(
                "new-time-entry", "valid-jwt", "NEW_TIME_ENTRY", "{\"id\":\"evt-123\"}"));

        verify(webhookTokenRepository).findByWorkspaceIdAndWebhookPath("ws-1", "/webhook/new-time-entry");
        verifyNoInteractions(webhookEventRepository);
    }

    private Map<String, Object> validClaims(String authToken) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("workspaceId", "ws-1");
        claims.put("addonId", "addon-1");
        claims.put("authToken", authToken);
        return claims;
    }

    private Installation activeInstallation() {
        Installation installation = new Installation();
        installation.setWorkspaceId("ws-1");
        installation.setAddonId("addon-1");
        return installation;
    }
}
