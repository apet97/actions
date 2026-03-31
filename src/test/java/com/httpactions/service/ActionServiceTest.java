package com.httpactions.service;

import com.httpactions.config.AddonConfig;
import com.httpactions.model.dto.ActionRequest;
import com.httpactions.model.dto.ActionResponse;
import com.httpactions.model.entity.Action;
import com.httpactions.model.enums.EventType;
import com.httpactions.model.enums.HttpMethod;
import com.httpactions.repository.ActionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ActionServiceTest {

    @Mock
    private ActionRepository actionRepository;

    @Mock
    private TokenService tokenService;

    @Mock
    private AddonConfig addonConfig;

    @InjectMocks
    private ActionService actionService;

    private static final String WORKSPACE_ID = "ws-test-123";

    private AddonConfig.Outbound outbound;

    @BeforeEach
    void setUp() {
        outbound = new AddonConfig.Outbound();
        // Default: allow HTTP (dev mode) so HTTPS validation doesn't interfere
        outbound.setAllowHttp(true);
    }

    // ── listActions ──

    @Test
    @DisplayName("listActions returns mapped responses")
    void listActions_returnsMappedResponses() {
        Action action1 = createTestAction(UUID.randomUUID(), "Slack Hook");
        Action action2 = createTestAction(UUID.randomUUID(), "Discord Hook");

        when(actionRepository.findByWorkspaceId(WORKSPACE_ID))
                .thenReturn(List.of(action1, action2));

        List<ActionResponse> result = actionService.listActions(WORKSPACE_ID);

        assertEquals(2, result.size());
        assertEquals("Slack Hook", result.get(0).getName());
        assertEquals("Discord Hook", result.get(1).getName());
        assertEquals(EventType.NEW_TIME_ENTRY, result.get(0).getEventType());
        assertEquals(HttpMethod.POST, result.get(0).getHttpMethod());
        verify(actionRepository).findByWorkspaceId(WORKSPACE_ID);
    }

    // ── createAction ──

    @Test
    @DisplayName("createAction success")
    void createAction_success() {
        when(addonConfig.getOutbound()).thenReturn(outbound);
        when(actionRepository.countByWorkspaceId(WORKSPACE_ID)).thenReturn(5L);
        when(actionRepository.save(any(Action.class))).thenAnswer(invocation -> {
            Action a = invocation.getArgument(0);
            a.setId(UUID.randomUUID());
            a.setCreatedAt(Instant.now());
            a.setUpdatedAt(Instant.now());
            return a;
        });

        ActionRequest request = createActionRequest("New Action", "https://example.com/hook");
        ActionResponse response = actionService.createAction(WORKSPACE_ID, request);

        assertNotNull(response);
        assertEquals("New Action", response.getName());
        assertEquals(EventType.NEW_TIME_ENTRY, response.getEventType());
        assertEquals(HttpMethod.POST, response.getHttpMethod());
        verify(actionRepository).countByWorkspaceId(WORKSPACE_ID);
        verify(actionRepository).save(any(Action.class));
    }

    @Test
    @DisplayName("createAction exceeding max (50) throws IllegalArgumentException")
    void createAction_exceedsMax_throwsIllegalArgumentException() {
        when(actionRepository.countByWorkspaceId(WORKSPACE_ID)).thenReturn(50L);

        ActionRequest request = createActionRequest("One Too Many", "https://example.com/hook");

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> actionService.createAction(WORKSPACE_ID, request));

        assertTrue(exception.getMessage().contains("50"));
        verify(actionRepository, never()).save(any());
    }

    @Test
    @DisplayName("createAction with non-HTTPS URL throws in prod")
    void createAction_nonHttpsUrl_throwsInProd() {
        outbound.setAllowHttp(false);
        when(addonConfig.getOutbound()).thenReturn(outbound);
        when(actionRepository.countByWorkspaceId(WORKSPACE_ID)).thenReturn(0L);

        ActionRequest request = createActionRequest("Insecure Action", "http://example.com/hook");

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> actionService.createAction(WORKSPACE_ID, request));

        assertTrue(exception.getMessage().contains("HTTPS"));
        verify(actionRepository, never()).save(any());
    }

    @Test
    @DisplayName("createAction with malformed absolute URL throws")
    void createAction_malformedAbsoluteUrl_throws() {
        when(actionRepository.countByWorkspaceId(WORKSPACE_ID)).thenReturn(0L);

        ActionRequest request = createActionRequest("Broken URL", "https://");

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> actionService.createAction(WORKSPACE_ID, request));

        assertTrue(exception.getMessage().contains("valid absolute HTTP or HTTPS URL"));
        verify(actionRepository, never()).save(any());
    }

    // ── updateAction ──

    @Test
    @DisplayName("updateAction success")
    void updateAction_success() {
        UUID actionId = UUID.randomUUID();
        Action existing = createTestAction(actionId, "Old Name");

        when(addonConfig.getOutbound()).thenReturn(outbound);
        when(actionRepository.findByIdAndWorkspaceId(actionId, WORKSPACE_ID))
                .thenReturn(Optional.of(existing));
        when(actionRepository.save(any(Action.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ActionRequest request = createActionRequest("Updated Name", "https://example.com/updated");
        ActionResponse response = actionService.updateAction(WORKSPACE_ID, actionId, request);

        assertNotNull(response);
        assertEquals("Updated Name", response.getName());
        assertEquals("https://example.com/updated", response.getUrlTemplate());
        verify(actionRepository).save(any(Action.class));
    }

    @Test
    @DisplayName("updateAction for non-existent throws NoSuchElementException")
    void updateAction_notFound_throwsNoSuchElementException() {
        UUID actionId = UUID.randomUUID();

        when(actionRepository.findByIdAndWorkspaceId(actionId, WORKSPACE_ID))
                .thenReturn(Optional.empty());

        ActionRequest request = createActionRequest("Missing", "https://example.com/hook");

        assertThrows(NoSuchElementException.class,
                () -> actionService.updateAction(WORKSPACE_ID, actionId, request));

        verify(actionRepository, never()).save(any());
    }

    // ── deleteAction ──

    @Test
    @DisplayName("deleteAction success")
    void deleteAction_success() {
        UUID actionId = UUID.randomUUID();
        Action existing = createTestAction(actionId, "To Delete");

        when(actionRepository.findByIdAndWorkspaceId(actionId, WORKSPACE_ID))
                .thenReturn(Optional.of(existing));

        actionService.deleteAction(WORKSPACE_ID, actionId);

        verify(actionRepository).delete(existing);
    }

    // ── actionExistsByName ──

    @Test
    @DisplayName("actionExistsByName returns true when exists")
    void actionExistsByName_returnsTrue() {
        when(actionRepository.existsByWorkspaceIdAndName(WORKSPACE_ID, "Slack Hook"))
                .thenReturn(true);

        assertTrue(actionService.actionExistsByName(WORKSPACE_ID, "Slack Hook"));
    }

    @Test
    @DisplayName("actionExistsByName returns false when not exists")
    void actionExistsByName_returnsFalse() {
        when(actionRepository.existsByWorkspaceIdAndName(WORKSPACE_ID, "Nonexistent"))
                .thenReturn(false);

        assertFalse(actionService.actionExistsByName(WORKSPACE_ID, "Nonexistent"));
    }

    // ── Helpers ──

    private Action createTestAction(UUID id, String name) {
        Action a = new Action();
        a.setId(id);
        a.setWorkspaceId(WORKSPACE_ID);
        a.setName(name);
        a.setEventType(EventType.NEW_TIME_ENTRY);
        a.setHttpMethod(HttpMethod.POST);
        a.setUrlTemplate("https://example.com/hook");
        a.setRetryCount(3);
        a.setEnabled(true);
        a.setCreatedAt(Instant.now());
        a.setUpdatedAt(Instant.now());
        return a;
    }

    private ActionRequest createActionRequest(String name, String url) {
        ActionRequest request = new ActionRequest();
        request.setName(name);
        request.setEventType(EventType.NEW_TIME_ENTRY);
        request.setHttpMethod(HttpMethod.POST);
        request.setUrlTemplate(url);
        request.setRetryCount(3);
        request.setEnabled(true);
        return request;
    }
}
