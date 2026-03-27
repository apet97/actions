package com.httpactions.controller;

import com.httpactions.model.enums.EventType;
import com.httpactions.repository.WebhookEventRepository;
import com.httpactions.repository.WebhookTokenRepository;
import com.httpactions.service.ExecutionService;
import com.httpactions.service.InstallationService;
import com.httpactions.service.TokenService;
import com.httpactions.model.entity.Installation;
import com.httpactions.model.entity.WebhookToken;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(
    controllers = WebhookController.class,
    excludeAutoConfiguration = SecurityAutoConfiguration.class
)
@AutoConfigureMockMvc(addFilters = false)
class WebhookControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TokenService tokenService;

    @MockitoBean
    private InstallationService installationService;

    @MockitoBean
    private ExecutionService executionService;

    @MockitoBean
    private WebhookEventRepository webhookEventRepository;

    @MockitoBean
    private WebhookTokenRepository webhookTokenRepository;

    private static final String WEBHOOK_PATH = "/webhook/new-time-entry";
    private static final String SIGNATURE_HEADER = "Clockify-Signature";
    private static final String EVENT_TYPE_HEADER = "clockify-webhook-event-type";
    private static final String VALID_JWT = "eyJ0eXAiOiJKV1QiLCJhbGciOiJSUzI1NiJ9.eyJpc3MiOiJjbG9ja2lmeSJ9.sig";
    private static final String WORKSPACE_ID = "ws-abc-123";
    private static final String WEBHOOK_BODY = """
            {"id":"te-123","description":"Test entry","userId":"user-456"}""";

    private Map<String, Object> validClaims() {
        Map<String, Object> claims = new HashMap<>();
        claims.put("workspaceId", WORKSPACE_ID);
        claims.put("iss", "clockify");
        claims.put("type", "addon");
        claims.put("sub", "addon-test");
        return claims;
    }

    private Installation activeInstallation() {
        Installation installation = new Installation();
        installation.setWorkspaceId(WORKSPACE_ID);
        installation.setAddonId("addon-test");
        return installation;
    }

    @Test
    @DisplayName("Valid webhook returns 200 and triggers async processing")
    void validWebhook_returns200_andProcesses() throws Exception {
        when(tokenService.verifyAndParseClaims(VALID_JWT)).thenReturn(validClaims());
        when(installationService.getActiveInstallation(WORKSPACE_ID))
                .thenReturn(Optional.of(activeInstallation()));
        // Per-webhook auth check (moved before idempotency in C3 fix)
        when(webhookTokenRepository.findByWorkspaceIdAndWebhookPath(eq(WORKSPACE_ID), any()))
                .thenReturn(Optional.empty());
        // Atomic idempotency insert (H6 fix: insertIfAbsent returns 1 for new events)
        when(webhookEventRepository.insertIfAbsent(eq(WORKSPACE_ID), any(), any()))
                .thenReturn(1);

        mockMvc.perform(post(WEBHOOK_PATH)
                        .header(SIGNATURE_HEADER, VALID_JWT)
                        .header(EVENT_TYPE_HEADER, "NEW_TIME_ENTRY")
                        .content(WEBHOOK_BODY)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());

        verify(executionService).processWebhookAsync(
                eq(WORKSPACE_ID), eq(EventType.NEW_TIME_ENTRY), eq(WEBHOOK_BODY));
    }

    @Test
    @DisplayName("Invalid signature returns 401")
    void invalidSignature_returns401() throws Exception {
        when(tokenService.verifyAndParseClaims("bad-jwt")).thenReturn(null);

        mockMvc.perform(post(WEBHOOK_PATH)
                        .header(SIGNATURE_HEADER, "bad-jwt")
                        .header(EVENT_TYPE_HEADER, "NEW_TIME_ENTRY")
                        .content(WEBHOOK_BODY)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(installationService);
        verifyNoInteractions(executionService);
    }

    @Test
    @DisplayName("Claims without workspaceId returns 401")
    void missingWorkspaceId_returns401() throws Exception {
        Map<String, Object> claimsWithoutWs = new HashMap<>();
        claimsWithoutWs.put("iss", "clockify");
        claimsWithoutWs.put("type", "addon");
        // No "workspaceId" key

        when(tokenService.verifyAndParseClaims(VALID_JWT)).thenReturn(claimsWithoutWs);

        mockMvc.perform(post(WEBHOOK_PATH)
                        .header(SIGNATURE_HEADER, VALID_JWT)
                        .header(EVENT_TYPE_HEADER, "NEW_TIME_ENTRY")
                        .content(WEBHOOK_BODY)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(installationService);
        verifyNoInteractions(executionService);
    }

    @Test
    @DisplayName("Inactive workspace returns 200 without processing")
    void inactiveWorkspace_returns200_noProcessing() throws Exception {
        when(tokenService.verifyAndParseClaims(VALID_JWT)).thenReturn(validClaims());
        when(installationService.getActiveInstallation(WORKSPACE_ID))
                .thenReturn(Optional.empty());

        mockMvc.perform(post(WEBHOOK_PATH)
                        .header(SIGNATURE_HEADER, VALID_JWT)
                        .header(EVENT_TYPE_HEADER, "NEW_TIME_ENTRY")
                        .content(WEBHOOK_BODY)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());

        verifyNoInteractions(executionService);
    }

    @Test
    @DisplayName("Unknown event type returns 200 without processing")
    void unknownEventType_returns200_noProcessing() throws Exception {
        when(tokenService.verifyAndParseClaims(VALID_JWT)).thenReturn(validClaims());
        when(installationService.getActiveInstallation(WORKSPACE_ID))
                .thenReturn(Optional.of(activeInstallation()));

        mockMvc.perform(post(WEBHOOK_PATH)
                        .header(SIGNATURE_HEADER, VALID_JWT)
                        .header(EVENT_TYPE_HEADER, "COMPLETELY_UNKNOWN_EVENT")
                        .content(WEBHOOK_BODY)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());

        verifyNoInteractions(executionService);
    }

    @Test
    @DisplayName("Slug/header mismatch returns 200 without processing")
    void slugHeaderMismatch_returns200_noProcessing() throws Exception {
        // Path slug is "new-time-entry" but header says TIME_ENTRY_UPDATED
        // TIME_ENTRY_UPDATED resolves to slug "time-entry-updated", which != "new-time-entry"
        when(tokenService.verifyAndParseClaims(VALID_JWT)).thenReturn(validClaims());
        when(installationService.getActiveInstallation(WORKSPACE_ID))
                .thenReturn(Optional.of(activeInstallation()));

        mockMvc.perform(post("/webhook/new-time-entry")
                        .header(SIGNATURE_HEADER, VALID_JWT)
                        .header(EVENT_TYPE_HEADER, "TIME_ENTRY_UPDATED")
                        .content(WEBHOOK_BODY)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());

        verifyNoInteractions(executionService);
    }

    @Test
    @DisplayName("Webhook addonId mismatch returns 401")
    void addonIdMismatch_returns401() throws Exception {
        Installation installation = activeInstallation();
        installation.setAddonId("different-addon");

        when(tokenService.verifyAndParseClaims(VALID_JWT)).thenReturn(validClaims());
        when(installationService.getActiveInstallation(WORKSPACE_ID))
                .thenReturn(Optional.of(installation));

        mockMvc.perform(post(WEBHOOK_PATH)
                        .header(SIGNATURE_HEADER, VALID_JWT)
                        .header(EVENT_TYPE_HEADER, "NEW_TIME_ENTRY")
                        .content(WEBHOOK_BODY)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(executionService);
    }

    @Test
    @DisplayName("Stored webhook auth token mismatch returns 401")
    void webhookAuthTokenMismatch_returns401() throws Exception {
        Map<String, Object> claims = validClaims();
        claims.put("authToken", "actual-token");

        WebhookToken storedToken = new WebhookToken();
        storedToken.setWorkspaceId(WORKSPACE_ID);
        storedToken.setWebhookPath("/webhook/new-time-entry");
        storedToken.setAuthTokenEnc("enc-token");

        when(tokenService.verifyAndParseClaims(VALID_JWT)).thenReturn(claims);
        when(installationService.getActiveInstallation(WORKSPACE_ID))
                .thenReturn(Optional.of(activeInstallation()));
        when(webhookTokenRepository.findByWorkspaceIdAndWebhookPath(WORKSPACE_ID, "/webhook/new-time-entry"))
                .thenReturn(Optional.of(storedToken));
        when(tokenService.decrypt("enc-token")).thenReturn("expected-token");

        mockMvc.perform(post(WEBHOOK_PATH)
                        .header(SIGNATURE_HEADER, VALID_JWT)
                        .header(EVENT_TYPE_HEADER, "NEW_TIME_ENTRY")
                        .content(WEBHOOK_BODY)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(executionService);
        verifyNoInteractions(webhookEventRepository);
    }
}
