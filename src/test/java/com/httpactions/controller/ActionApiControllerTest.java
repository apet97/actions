package com.httpactions.controller;

import com.httpactions.model.dto.ActionResponse;
import com.httpactions.model.dto.ExecutionLogResponse;
import com.httpactions.model.dto.TestResult;
import com.httpactions.model.dto.VerifiedAddonContext;
import com.httpactions.model.entity.Action;
import com.httpactions.model.enums.EventType;
import com.httpactions.model.enums.HttpMethod;
import com.httpactions.service.ActionService;
import com.httpactions.service.ActionTemplateService;
import com.httpactions.service.ActionTemplateService.ActionTemplate;
import com.httpactions.service.EventSchemaRegistry;
import com.httpactions.service.ExecutionService;
import com.httpactions.service.LogService;
import com.httpactions.service.VerifiedAddonContextService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(
    controllers = ActionApiController.class,
    excludeAutoConfiguration = SecurityAutoConfiguration.class
)
@AutoConfigureMockMvc(addFilters = false)
class ActionApiControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ActionService actionService;

    @MockitoBean
    private ExecutionService executionService;

    @MockitoBean
    private LogService logService;

    @MockitoBean
    private VerifiedAddonContextService verifiedAddonContextService;

    @MockitoBean
    private EventSchemaRegistry eventSchemaRegistry;

    @MockitoBean
    private ActionTemplateService actionTemplateService;

    private static final String TOKEN_HEADER = "X-Addon-Token";
    private static final String VALID_TOKEN = "valid-addon-token-jwt";
    private static final String WORKSPACE_ID = "ws-test-789";

    private VerifiedAddonContext validContext() {
        return new VerifiedAddonContext(
                WORKSPACE_ID,
                "addon-test",
                "https://app.clockify.me/api",
                "en",
                "",
                "DEFAULT",
                Map.of("workspaceId", WORKSPACE_ID)
        );
    }

    private ActionResponse sampleActionResponse() {
        return actionResponse(UUID.randomUUID(), "Test Action");
    }

    private ActionResponse actionResponse(UUID id, String name) {
        return new ActionResponse(
                id,
                name,
                EventType.NEW_TIME_ENTRY,
                true,
                HttpMethod.POST,
                "https://example.com/hook",
                Map.of(),
                null,
                false,
                3,
                Instant.now(),
                Instant.now(),
                null,
                null,
                null,
                null
        );
    }

    private Page<ExecutionLogResponse> sampleLogPage(UUID actionId) {
        ExecutionLogResponse log = new ExecutionLogResponse();
        log.setId(1L);
        log.setActionId(actionId);
        log.setEventType("NEW_TIME_ENTRY");
        log.setResponseStatus(200);
        log.setResponseTimeMs(42);
        log.setSuccess(true);
        log.setExecutedAt(Instant.parse("2026-03-28T12:00:00Z"));
        return new PageImpl<>(List.of(log));
    }

    // ── GET /api/actions ──

    @Test
    @DisplayName("List actions with valid token returns action list")
    void listActions_validToken_returnsActions() throws Exception {
        when(verifiedAddonContextService.verifyToken(VALID_TOKEN)).thenReturn(Optional.of(validContext()));

        ActionResponse action1 = actionResponse(UUID.randomUUID(), "Slack Notification");
        ActionResponse action2 = actionResponse(UUID.randomUUID(), "Discord Webhook");

        when(actionService.listActions(WORKSPACE_ID)).thenReturn(List.of(action1, action2));

        mockMvc.perform(get("/api/actions")
                        .header(TOKEN_HEADER, VALID_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].name", is("Slack Notification")))
                .andExpect(jsonPath("$[1].name", is("Discord Webhook")));

        verify(actionService).listActions(WORKSPACE_ID);
    }

    @Test
    @DisplayName("List actions with invalid token returns 401")
    void listActions_invalidToken_returns401() throws Exception {
        when(verifiedAddonContextService.verifyToken("bad-token")).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/actions")
                        .header(TOKEN_HEADER, "bad-token"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(actionService);
    }

    // ── POST /api/actions ──

    @Test
    @DisplayName("Create action with valid request returns 201")
    void createAction_validRequest_returns201() throws Exception {
        when(verifiedAddonContextService.verifyToken(VALID_TOKEN)).thenReturn(Optional.of(validContext()));

        ActionResponse created = actionResponse(UUID.randomUUID(), "New Webhook Action");
        when(actionService.createAction(eq(WORKSPACE_ID), any())).thenReturn(created);

        String requestBody = """
                {
                    "name": "New Webhook Action",
                    "eventType": "NEW_TIME_ENTRY",
                    "httpMethod": "POST",
                    "urlTemplate": "https://example.com/hook",
                    "retryCount": 0,
                    "enabled": true
                }
                """;

        mockMvc.perform(post("/api/actions")
                        .header(TOKEN_HEADER, VALID_TOKEN)
                        .content(requestBody)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name", is("New Webhook Action")))
                .andExpect(jsonPath("$.id", notNullValue()));

        verify(actionService).createAction(eq(WORKSPACE_ID), any());
    }

    @Test
    @DisplayName("Create action with invalid token returns 401")
    void createAction_invalidToken_returns401() throws Exception {
        when(verifiedAddonContextService.verifyToken("bad-token")).thenReturn(Optional.empty());

        String requestBody = """
                {
                    "name": "Some Action",
                    "eventType": "NEW_TIME_ENTRY",
                    "httpMethod": "POST",
                    "urlTemplate": "https://example.com/hook",
                    "retryCount": 0,
                    "enabled": true
                }
                """;

        mockMvc.perform(post("/api/actions")
                        .header(TOKEN_HEADER, "bad-token")
                        .content(requestBody)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(actionService);
    }

    @Test
    @DisplayName("Create action with invalid payload returns structured validation error")
    void createAction_invalidPayload_returnsStructuredError() throws Exception {
        when(verifiedAddonContextService.verifyToken(VALID_TOKEN)).thenReturn(Optional.of(validContext()));

        String requestBody = """
                {
                    "name": "Bad*Name",
                    "eventType": "NEW_TIME_ENTRY",
                    "httpMethod": "POST",
                    "urlTemplate": "https://example.com/hook"
                }
                """;

        mockMvc.perform(post("/api/actions")
                        .header(TOKEN_HEADER, VALID_TOKEN)
                        .content(requestBody)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", is("Validation failed")))
                .andExpect(jsonPath("$.fieldErrors.name", containsString("letters, numbers, spaces, and dashes")));
    }

    @Test
    @DisplayName("Create action with missing required fields returns structured validation error")
    void createAction_missingRequiredFields_returnsStructuredError() throws Exception {
        when(verifiedAddonContextService.verifyToken(VALID_TOKEN)).thenReturn(Optional.of(validContext()));

        String requestBody = """
                {
                    "name": "Missing Fields",
                    "eventType": "NEW_TIME_ENTRY",
                    "urlTemplate": "   "
                }
                """;

        mockMvc.perform(post("/api/actions")
                        .header(TOKEN_HEADER, VALID_TOKEN)
                        .content(requestBody)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", is("Validation failed")))
                .andExpect(jsonPath("$.fieldErrors.httpMethod", containsString("must not be null")))
                .andExpect(jsonPath("$.fieldErrors.urlTemplate", containsString("must not be blank")));

        verifyNoInteractions(actionService);
    }

    @Test
    @DisplayName("Update action with valid request returns 200")
    void updateAction_validRequest_returns200() throws Exception {
        UUID actionId = UUID.randomUUID();
        when(verifiedAddonContextService.verifyToken(VALID_TOKEN)).thenReturn(Optional.of(validContext()));

        ActionResponse updated = actionResponse(actionId, "Updated Action");
        when(actionService.updateAction(eq(WORKSPACE_ID), eq(actionId), any())).thenReturn(updated);

        String requestBody = """
                {
                    "name": "Updated Action",
                    "eventType": "NEW_TIME_ENTRY",
                    "httpMethod": "POST",
                    "urlTemplate": "https://example.com/hook",
                    "retryCount": 1,
                    "enabled": true
                }
                """;

        mockMvc.perform(put("/api/actions/{id}", actionId)
                        .header(TOKEN_HEADER, VALID_TOKEN)
                        .content(requestBody)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name", is("Updated Action")));
    }

    @Test
    @DisplayName("Update action missing resource returns structured 404 JSON")
    void updateAction_missingAction_returnsStructured404() throws Exception {
        UUID actionId = UUID.randomUUID();
        when(verifiedAddonContextService.verifyToken(VALID_TOKEN)).thenReturn(Optional.of(validContext()));
        when(actionService.updateAction(eq(WORKSPACE_ID), eq(actionId), any()))
                .thenThrow(new NoSuchElementException("Action not found"));

        String requestBody = """
                {
                    "name": "Updated Action",
                    "eventType": "NEW_TIME_ENTRY",
                    "httpMethod": "POST",
                    "urlTemplate": "https://example.com/hook",
                    "retryCount": 1,
                    "enabled": true
                }
                """;

        mockMvc.perform(put("/api/actions/{id}", actionId)
                        .header(TOKEN_HEADER, VALID_TOKEN)
                        .content(requestBody)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message", is("Action not found")))
                .andExpect(jsonPath("$.path", is("/api/actions/" + actionId)));
    }

    @Test
    @DisplayName("Delete action returns 204")
    void deleteAction_returns204() throws Exception {
        UUID actionId = UUID.randomUUID();
        when(verifiedAddonContextService.verifyToken(VALID_TOKEN)).thenReturn(Optional.of(validContext()));

        mockMvc.perform(delete("/api/actions/{id}", actionId)
                        .header(TOKEN_HEADER, VALID_TOKEN))
                .andExpect(status().isNoContent());

        verify(actionService).deleteAction(WORKSPACE_ID, actionId);
    }

    @Test
    @DisplayName("Delete action missing resource returns structured 404 JSON")
    void deleteAction_missingAction_returnsStructured404() throws Exception {
        UUID actionId = UUID.randomUUID();
        when(verifiedAddonContextService.verifyToken(VALID_TOKEN)).thenReturn(Optional.of(validContext()));
        doThrow(new NoSuchElementException("Action not found"))
                .when(actionService).deleteAction(WORKSPACE_ID, actionId);

        mockMvc.perform(delete("/api/actions/{id}", actionId)
                        .header(TOKEN_HEADER, VALID_TOKEN))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message", is("Action not found")))
                .andExpect(jsonPath("$.path", is("/api/actions/" + actionId)));
    }

    @Test
    @DisplayName("Test action returns test result")
    void testAction_returnsResult() throws Exception {
        UUID actionId = UUID.randomUUID();
        when(verifiedAddonContextService.verifyToken(VALID_TOKEN)).thenReturn(Optional.of(validContext()));

        Action action = new Action();
        action.setId(actionId);
        action.setWorkspaceId(WORKSPACE_ID);
        action.setEventType(EventType.NEW_TIME_ENTRY);
        when(actionService.findActionRaw(WORKSPACE_ID, actionId)).thenReturn(java.util.Optional.of(action));

        TestResult result = new TestResult();
        result.setResponseStatus(200);
        result.setResponseBody("{\"ok\":true}");
        result.setSuccess(true);
        when(executionService.testAction(eq(action), any())).thenReturn(result);

        mockMvc.perform(post("/api/actions/{id}/test", actionId)
                        .header(TOKEN_HEADER, VALID_TOKEN)
                        .content("{}")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.responseStatus", is(200)))
                .andExpect(jsonPath("$.success", is(true)));
    }

    @Test
    @DisplayName("Test action missing resource returns structured 404 JSON")
    void testAction_missingAction_returnsStructured404() throws Exception {
        UUID actionId = UUID.randomUUID();
        when(verifiedAddonContextService.verifyToken(VALID_TOKEN)).thenReturn(Optional.of(validContext()));
        when(actionService.findActionRaw(WORKSPACE_ID, actionId)).thenReturn(Optional.empty());

        mockMvc.perform(post("/api/actions/{id}/test", actionId)
                        .header(TOKEN_HEADER, VALID_TOKEN)
                        .content("{}")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message", is("Action not found")))
                .andExpect(jsonPath("$.path", is("/api/actions/" + actionId + "/test")));
    }

    @Test
    @DisplayName("Get action logs clamps invalid paging and returns page content")
    void getActionLogs_clampsPagingAndReturnsPage() throws Exception {
        UUID actionId = UUID.randomUUID();
        when(verifiedAddonContextService.verifyToken(VALID_TOKEN)).thenReturn(Optional.of(validContext()));
        when(actionService.getAction(WORKSPACE_ID, actionId)).thenReturn(Optional.of(sampleActionResponse()));
        when(logService.getLogsForAction(actionId, 0, 1)).thenReturn(sampleLogPage(actionId));

        mockMvc.perform(get("/api/actions/{id}/logs", actionId)
                        .header(TOKEN_HEADER, VALID_TOKEN)
                        .param("page", "-4")
                        .param("size", "0"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].responseStatus", is(200)));

        verify(logService).getLogsForAction(actionId, 0, 1);
    }

    @Test
    @DisplayName("Get workspace logs clamps invalid paging and returns page content")
    void getWorkspaceLogs_clampsPagingAndReturnsPage() throws Exception {
        UUID actionId = UUID.randomUUID();
        when(verifiedAddonContextService.verifyToken(VALID_TOKEN)).thenReturn(Optional.of(validContext()));
        when(logService.getLogsForWorkspace(WORKSPACE_ID, 0, 1)).thenReturn(sampleLogPage(actionId));

        mockMvc.perform(get("/api/logs")
                        .header(TOKEN_HEADER, VALID_TOKEN)
                        .param("page", "-2")
                        .param("size", "0"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].actionId", is(actionId.toString())));

        verify(logService).getLogsForWorkspace(WORKSPACE_ID, 0, 1);
    }

    @Test
    @DisplayName("Get action logs missing resource returns structured 404 JSON")
    void getActionLogs_missingAction_returnsStructured404() throws Exception {
        UUID actionId = UUID.randomUUID();
        when(verifiedAddonContextService.verifyToken(VALID_TOKEN)).thenReturn(Optional.of(validContext()));
        when(actionService.getAction(WORKSPACE_ID, actionId)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/actions/{id}/logs", actionId)
                        .header(TOKEN_HEADER, VALID_TOKEN))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message", is("Action not found")))
                .andExpect(jsonPath("$.path", is("/api/actions/" + actionId + "/logs")));
    }

    @Test
    @DisplayName("List events returns schemas for all event types")
    void listEvents_returnsSchemas() throws Exception {
        when(verifiedAddonContextService.verifyToken(VALID_TOKEN)).thenReturn(Optional.of(validContext()));
        when(eventSchemaRegistry.getSchema(any())).thenReturn(List.of(
                new EventSchemaRegistry.EventVariable("event.id", "string", "Event ID")
        ));

        mockMvc.perform(get("/api/events")
                        .header(TOKEN_HEADER, VALID_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(EventType.values().length)))
                .andExpect(jsonPath("$[0].variables[0].path", is("event.id")));
    }

    @Test
    @DisplayName("Export actions returns exported list")
    void exportActions_returnsList() throws Exception {
        when(verifiedAddonContextService.verifyToken(VALID_TOKEN)).thenReturn(Optional.of(validContext()));
        when(actionService.listActions(WORKSPACE_ID)).thenReturn(List.of(sampleActionResponse()));

        mockMvc.perform(get("/api/actions/export")
                        .header(TOKEN_HEADER, VALID_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].name", is("Test Action")));
    }

    @Test
    @DisplayName("Import actions rejects invalid JSON with structured 400")
    void importActions_invalidJson_returnsStructured400() throws Exception {
        when(verifiedAddonContextService.verifyToken(VALID_TOKEN)).thenReturn(Optional.of(validContext()));

        mockMvc.perform(post("/api/actions/import")
                        .header(TOKEN_HEADER, VALID_TOKEN)
                        .content("{not valid json")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", is("Invalid JSON array")))
                .andExpect(jsonPath("$.path", is("/api/actions/import")));
    }

    @Test
    @DisplayName("Import actions rejects oversized arrays early")
    void importActions_oversizedList_returnsStructured400() throws Exception {
        when(verifiedAddonContextService.verifyToken(VALID_TOKEN)).thenReturn(Optional.of(validContext()));

        StringBuilder body = new StringBuilder("[");
        for (int i = 0; i < 51; i++) {
            if (i > 0) {
                body.append(',');
            }
            body.append("""
                    {
                      "name":"Imported Action %d",
                      "eventType":"NEW_TIME_ENTRY",
                      "httpMethod":"POST",
                      "urlTemplate":"https://example.com/hook",
                      "retryCount":1,
                      "enabled":true
                    }
                    """.formatted(i));
        }
        body.append(']');

        mockMvc.perform(post("/api/actions/import")
                        .header(TOKEN_HEADER, VALID_TOKEN)
                        .content(body.toString())
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", is("Import limited to 50 actions")));
    }

    @Test
    @DisplayName("Import actions returns created and skipped counts")
    void importActions_returnsCreatedAndSkipped() throws Exception {
        when(verifiedAddonContextService.verifyToken(VALID_TOKEN)).thenReturn(Optional.of(validContext()));
        when(actionService.actionExistsByName(WORKSPACE_ID, "Imported Action")).thenReturn(false);
        when(actionService.createAction(eq(WORKSPACE_ID), any())).thenReturn(sampleActionResponse());

        String requestBody = """
                [
                  {
                    "name": "Imported Action",
                    "eventType": "NEW_TIME_ENTRY",
                    "httpMethod": "POST",
                    "urlTemplate": "https://example.com/hook",
                    "retryCount": 1,
                    "enabled": true
                  }
                ]
                """;

        mockMvc.perform(post("/api/actions/import")
                        .header(TOKEN_HEADER, VALID_TOKEN)
                        .content(requestBody)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.created", hasSize(1)))
                .andExpect(jsonPath("$.skipped", hasSize(0)));
    }

    @Test
    @DisplayName("List templates returns configured templates")
    void listTemplates_returnsTemplates() throws Exception {
        when(verifiedAddonContextService.verifyToken(VALID_TOKEN)).thenReturn(Optional.of(validContext()));
        when(actionTemplateService.getTemplates()).thenReturn(List.of(
                new ActionTemplate("slack-webhook", "Slack", "desc", "Messaging",
                        "NEW_TIME_ENTRY", "POST", "https://example.com", "{}", Map.of())
        ));

        mockMvc.perform(get("/api/templates")
                        .header(TOKEN_HEADER, VALID_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id", is("slack-webhook")));
    }
}
