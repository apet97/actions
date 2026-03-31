package com.httpactions.service;

import com.httpactions.model.entity.Installation;
import com.httpactions.model.entity.WebhookToken;
import com.httpactions.model.enums.InstallationStatus;
import com.httpactions.repository.InstallationRepository;
import com.httpactions.repository.WebhookTokenRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InstallationServiceTest {

    @Mock
    private InstallationRepository installationRepository;

    @Mock
    private WebhookTokenRepository webhookTokenRepository;

    @Mock
    private TokenService tokenService;

    @InjectMocks
    private InstallationService installationService;

    // ---------------------------------------------------------------
    // handleInstalled
    // ---------------------------------------------------------------

    @Test
    void handleInstalled_storesInstallationAndWebhookTokens() {
        String body = """
                {
                  "workspaceId": "ws-1",
                  "addonId": "addon-1",
                  "authToken": "install-token-abc",
                  "apiUrl": "https://api.clockify.me/api",
                  "addonUserId": "user-42",
                  "webhooks": [
                    {"path": "/webhook/new-time-entry", "authToken": "wh-token-1"},
                    {"path": "/webhook/time-entry-updated", "authToken": "wh-token-2"}
                  ]
                }
                """;

        when(installationRepository.findById("ws-1")).thenReturn(Optional.empty());
        when(tokenService.encrypt("install-token-abc")).thenReturn("enc-install-token");
        when(tokenService.encrypt("wh-token-1")).thenReturn("enc-wh-1");
        when(tokenService.encrypt("wh-token-2")).thenReturn("enc-wh-2");
        when(webhookTokenRepository.findAllByWorkspaceId("ws-1")).thenReturn(List.of());

        installationService.handleInstalled(body);

        // Verify installation saved
        ArgumentCaptor<Installation> installCaptor = ArgumentCaptor.forClass(Installation.class);
        verify(installationRepository).save(installCaptor.capture());
        Installation saved = installCaptor.getValue();
        assertEquals("ws-1", saved.getWorkspaceId());
        assertEquals("addon-1", saved.getAddonId());
        assertEquals("enc-install-token", saved.getAuthTokenEnc());
        assertEquals("https://api.clockify.me/api", saved.getApiUrl());
        assertEquals("user-42", saved.getAddonUserId());
        assertEquals(InstallationStatus.ACTIVE, saved.getStatus());

        // Verify webhook tokens saved (2 webhooks)
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<WebhookToken>> whCaptor = ArgumentCaptor.forClass(List.class);
        verify(webhookTokenRepository).saveAll(whCaptor.capture());
        var savedTokens = whCaptor.getValue();

        assertEquals("ws-1", savedTokens.get(0).getWorkspaceId());
        assertEquals("/webhook/new-time-entry", savedTokens.get(0).getWebhookPath());
        assertEquals("enc-wh-1", savedTokens.get(0).getAuthTokenEnc());

        assertEquals("ws-1", savedTokens.get(1).getWorkspaceId());
        assertEquals("/webhook/time-entry-updated", savedTokens.get(1).getWebhookPath());
        assertEquals("enc-wh-2", savedTokens.get(1).getAuthTokenEnc());
    }

    @Test
    void handleInstalled_missingWorkspaceId_throwsIllegalArgumentException() {
        String body = """
                {
                  "addonId": "addon-1",
                  "authToken": "some-token"
                }
                """;

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> installationService.handleInstalled(body));

        assertTrue(ex.getMessage().contains("missing required fields"));
        verifyNoInteractions(installationRepository);
        verifyNoInteractions(webhookTokenRepository);
        verifyNoInteractions(tokenService);
    }

    @Test
    void handleInstalled_missingAuthToken_throwsIllegalArgumentException() {
        String body = """
                {
                  "workspaceId": "ws-1",
                  "addonId": "addon-1"
                }
                """;

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> installationService.handleInstalled(body));

        assertTrue(ex.getMessage().contains("missing required fields"));
        verifyNoInteractions(installationRepository);
    }

    // ---------------------------------------------------------------
    // handleDeleted
    // ---------------------------------------------------------------

    @Test
    void handleDeleted_removesInstallation() {
        String body = """
                {
                  "workspaceId": "ws-1"
                }
                """;

        installationService.handleDeleted(body);

        verify(installationRepository).deleteById("ws-1");
    }

    @Test
    void handleDeleted_missingWorkspaceId_throwsIllegalArgumentException() {
        String body = """
                {
                  "addonId": "addon-1"
                }
                """;

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> installationService.handleDeleted(body));

        assertTrue(ex.getMessage().contains("missing workspaceId"));
        verify(installationRepository, never()).deleteById(anyString());
    }

    // ---------------------------------------------------------------
    // handleStatusChanged
    // ---------------------------------------------------------------

    @Test
    void handleStatusChanged_updatesStatus() {
        String body = """
                {
                  "workspaceId": "ws-1",
                  "status": "INACTIVE"
                }
                """;

        Installation existing = new Installation();
        existing.setWorkspaceId("ws-1");
        existing.setStatus(InstallationStatus.ACTIVE);

        when(installationRepository.findById("ws-1")).thenReturn(Optional.of(existing));

        installationService.handleStatusChanged(body);

        assertEquals(InstallationStatus.INACTIVE, existing.getStatus());
        verify(installationRepository).save(existing);
    }

    @Test
    void handleStatusChanged_unknownStatus_throwsIllegalArgumentException() {
        String body = """
                {
                  "workspaceId": "ws-1",
                  "status": "SUSPENDED"
                }
                """;

        Installation existing = new Installation();
        existing.setWorkspaceId("ws-1");
        existing.setStatus(InstallationStatus.ACTIVE);

        when(installationRepository.findById("ws-1")).thenReturn(Optional.of(existing));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> installationService.handleStatusChanged(body));

        assertTrue(ex.getMessage().contains("Unknown installation status"));
        assertEquals(InstallationStatus.ACTIVE, existing.getStatus());
        verify(installationRepository, never()).save(any());
    }

    @Test
    void handleStatusChanged_missingWorkspaceId_throwsIllegalArgumentException() {
        String body = """
                {
                  "status": "INACTIVE"
                }
                """;

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> installationService.handleStatusChanged(body));

        assertTrue(ex.getMessage().contains("missing required fields"));
        verifyNoInteractions(installationRepository);
    }

    @Test
    void handleStatusChanged_missingStatus_throwsIllegalArgumentException() {
        String body = """
                {
                  "workspaceId": "ws-1"
                }
                """;

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> installationService.handleStatusChanged(body));

        assertTrue(ex.getMessage().contains("missing required fields"));
        verifyNoInteractions(installationRepository);
    }

    // ---------------------------------------------------------------
    // getActiveInstallation
    // ---------------------------------------------------------------

    @Test
    void getActiveInstallation_active_returnsInstallation() {
        Installation active = new Installation();
        active.setWorkspaceId("ws-1");
        active.setStatus(InstallationStatus.ACTIVE);

        when(installationRepository.findById("ws-1")).thenReturn(Optional.of(active));

        Optional<Installation> result = installationService.getActiveInstallation("ws-1");

        assertTrue(result.isPresent());
        assertEquals("ws-1", result.get().getWorkspaceId());
    }

    @Test
    void getActiveInstallation_inactive_returnsEmpty() {
        Installation inactive = new Installation();
        inactive.setWorkspaceId("ws-1");
        inactive.setStatus(InstallationStatus.INACTIVE);

        when(installationRepository.findById("ws-1")).thenReturn(Optional.of(inactive));

        Optional<Installation> result = installationService.getActiveInstallation("ws-1");

        assertTrue(result.isEmpty());
    }

    @Test
    void getActiveInstallation_notFound_returnsEmpty() {
        when(installationRepository.findById("ws-missing")).thenReturn(Optional.empty());

        Optional<Installation> result = installationService.getActiveInstallation("ws-missing");

        assertTrue(result.isEmpty());
    }
}
