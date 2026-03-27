package com.httpactions.controller;

import com.httpactions.service.InstallationService;
import com.httpactions.service.TokenService;
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

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(
    controllers = LifecycleController.class,
    excludeAutoConfiguration = SecurityAutoConfiguration.class
)
@AutoConfigureMockMvc(addFilters = false)
class LifecycleControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private InstallationService installationService;

    @MockitoBean
    private TokenService tokenService;

    private static final String LIFECYCLE_TOKEN_HEADER = "X-Addon-Lifecycle-Token";
    private static final String VALID_TOKEN = "valid-lifecycle-jwt";
    private static final String INSTALLED_BODY = """
            {"workspaceId":"ws-1","addonId":"addon-1","authToken":"tok-123","webhooks":[]}""";
    private static final String DELETED_BODY = """
            {"workspaceId":"ws-1"}""";
    private static final String STATUS_CHANGED_BODY = """
            {"workspaceId":"ws-1","status":"ACTIVE"}""";

    private Map<String, Object> validClaims() {
        Map<String, Object> claims = new HashMap<>();
        claims.put("workspaceId", "ws-1");
        claims.put("iss", "clockify");
        claims.put("type", "addon");
        return claims;
    }

    // ── installed ──

    @Test
    @DisplayName("installed with valid token returns 200")
    void installed_validToken_returns200() throws Exception {
        when(tokenService.verifyAndParseClaims(VALID_TOKEN)).thenReturn(validClaims());

        mockMvc.perform(post("/lifecycle/installed")
                        .header(LIFECYCLE_TOKEN_HEADER, VALID_TOKEN)
                        .content(INSTALLED_BODY)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());

        verify(installationService).handleInstalled(INSTALLED_BODY);
    }

    @Test
    @DisplayName("installed with missing token returns 401 (H8)")
    void installed_missingToken_returns401() throws Exception {
        mockMvc.perform(post("/lifecycle/installed")
                        .content(INSTALLED_BODY)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(installationService);
    }

    @Test
    @DisplayName("installed with invalid token returns 401")
    void installed_invalidToken_returns401() throws Exception {
        when(tokenService.verifyAndParseClaims("bad-token")).thenReturn(null);

        mockMvc.perform(post("/lifecycle/installed")
                        .header(LIFECYCLE_TOKEN_HEADER, "bad-token")
                        .content(INSTALLED_BODY)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(installationService);
    }

    // ── deleted ──

    @Test
    @DisplayName("deleted with valid token returns 200")
    void deleted_validToken_returns200() throws Exception {
        when(tokenService.verifyAndParseClaims(VALID_TOKEN)).thenReturn(validClaims());

        mockMvc.perform(post("/lifecycle/deleted")
                        .header(LIFECYCLE_TOKEN_HEADER, VALID_TOKEN)
                        .content(DELETED_BODY)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());

        verify(installationService).handleDeleted(DELETED_BODY);
    }

    // ── status-changed ──

    @Test
    @DisplayName("status-changed with valid token returns 200")
    void statusChanged_validToken_returns200() throws Exception {
        when(tokenService.verifyAndParseClaims(VALID_TOKEN)).thenReturn(validClaims());

        mockMvc.perform(post("/lifecycle/status-changed")
                        .header(LIFECYCLE_TOKEN_HEADER, VALID_TOKEN)
                        .content(STATUS_CHANGED_BODY)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());

        verify(installationService).handleStatusChanged(STATUS_CHANGED_BODY);
    }

    // ── settings-updated ──

    private static final String SETTINGS_UPDATED_BODY = """
            {"workspaceId":"ws-1","settings":[{"id":"s1","value":"v1"}]}""";

    @Test
    @DisplayName("settings-updated with valid token returns 200")
    void settingsUpdated_validToken_returns200() throws Exception {
        when(tokenService.verifyAndParseClaims(VALID_TOKEN)).thenReturn(validClaims());

        mockMvc.perform(post("/lifecycle/settings-updated")
                        .header(LIFECYCLE_TOKEN_HEADER, VALID_TOKEN)
                        .content(SETTINGS_UPDATED_BODY)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());

        verify(installationService).handleSettingsUpdated(SETTINGS_UPDATED_BODY);
    }

    @Test
    @DisplayName("settings-updated with invalid token returns 401")
    void settingsUpdated_invalidToken_returns401() throws Exception {
        when(tokenService.verifyAndParseClaims("bad-token")).thenReturn(null);

        mockMvc.perform(post("/lifecycle/settings-updated")
                        .header(LIFECYCLE_TOKEN_HEADER, "bad-token")
                        .content(SETTINGS_UPDATED_BODY)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(installationService);
    }
}
