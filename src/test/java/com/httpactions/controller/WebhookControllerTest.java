package com.httpactions.controller;

import com.httpactions.model.enums.EventType;
import com.httpactions.service.ExecutionService;
import com.httpactions.service.VerifiedAddonContextService;
import com.httpactions.service.WebhookRequestService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(
        controllers = WebhookController.class,
        excludeAutoConfiguration = SecurityAutoConfiguration.class
)
@AutoConfigureMockMvc(addFilters = false)
@Import(ApiExceptionHandler.class)
class WebhookControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ExecutionService executionService;

    @MockitoBean
    private WebhookRequestService webhookRequestService;

    @MockitoBean
    private VerifiedAddonContextService verifiedAddonContextService;

    private static final String WEBHOOK_PATH = "/webhook/new-time-entry";
    private static final String SIGNATURE_HEADER = "Clockify-Signature";
    private static final String EVENT_TYPE_HEADER = "clockify-webhook-event-type";
    private static final String VALID_JWT = "valid-jwt";
    private static final String WORKSPACE_ID = "ws-abc-123";
    private static final String WEBHOOK_BODY = """
            {"id":"te-123","description":"Test entry","userId":"user-456"}""";

    @Test
    @DisplayName("Valid webhook returns 200 and triggers async processing")
    void validWebhook_returns200_andProcesses() throws Exception {
        when(webhookRequestService.resolve("new-time-entry", VALID_JWT, "NEW_TIME_ENTRY", WEBHOOK_BODY))
                .thenReturn(Optional.of(new WebhookRequestService.ProcessableWebhookEvent(
                        WORKSPACE_ID, EventType.NEW_TIME_ENTRY, WEBHOOK_BODY)));

        mockMvc.perform(post(WEBHOOK_PATH)
                        .header(SIGNATURE_HEADER, VALID_JWT)
                        .header(EVENT_TYPE_HEADER, "NEW_TIME_ENTRY")
                        .content(WEBHOOK_BODY)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());

        verify(executionService).processWebhookAsync(WORKSPACE_ID, EventType.NEW_TIME_ENTRY, WEBHOOK_BODY);
    }

    @Test
    @DisplayName("Missing required headers returns 401")
    void missingHeaders_returns401() throws Exception {
        when(webhookRequestService.resolve("new-time-entry", null, null, WEBHOOK_BODY))
                .thenThrow(new UnauthorizedAddonException("Missing required webhook headers"));

        mockMvc.perform(post(WEBHOOK_PATH)
                        .content(WEBHOOK_BODY)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(executionService);
    }

    @Test
    @DisplayName("Invalid signature returns 401")
    void invalidSignature_returns401() throws Exception {
        when(webhookRequestService.resolve("new-time-entry", "bad-jwt", "NEW_TIME_ENTRY", WEBHOOK_BODY))
                .thenThrow(new UnauthorizedAddonException("Invalid webhook signature"));

        mockMvc.perform(post(WEBHOOK_PATH)
                        .header(SIGNATURE_HEADER, "bad-jwt")
                        .header(EVENT_TYPE_HEADER, "NEW_TIME_ENTRY")
                        .content(WEBHOOK_BODY)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(executionService);
    }

    @Test
    @DisplayName("Duplicate delivery returns 200 without processing")
    void duplicateDelivery_returns200_noProcessing() throws Exception {
        when(webhookRequestService.resolve("new-time-entry", VALID_JWT, "NEW_TIME_ENTRY", WEBHOOK_BODY))
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
        when(webhookRequestService.resolve("new-time-entry", VALID_JWT, "COMPLETELY_UNKNOWN_EVENT", WEBHOOK_BODY))
                .thenReturn(Optional.empty());

        mockMvc.perform(post(WEBHOOK_PATH)
                        .header(SIGNATURE_HEADER, VALID_JWT)
                        .header(EVENT_TYPE_HEADER, "COMPLETELY_UNKNOWN_EVENT")
                        .content(WEBHOOK_BODY)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());

        verifyNoInteractions(executionService);
    }
}
