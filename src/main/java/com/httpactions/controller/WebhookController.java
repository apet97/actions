package com.httpactions.controller;

import com.httpactions.service.ExecutionService;
import com.httpactions.service.WebhookRequestService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;

@RestController
@RequestMapping("/webhook")
public class WebhookController {

    private static final Logger log = LoggerFactory.getLogger(WebhookController.class);

    private final ExecutionService executionService;
    private final WebhookRequestService webhookRequestService;

    public WebhookController(ExecutionService executionService,
                             WebhookRequestService webhookRequestService) {
        this.executionService = executionService;
        this.webhookRequestService = webhookRequestService;
    }

    @PostMapping("/{eventSlug}")
    @Transactional
    public ResponseEntity<Void> handleWebhook(
            @PathVariable String eventSlug,
            @RequestHeader(value = "Clockify-Signature", required = false) String signature,
            @RequestHeader(value = "clockify-webhook-event-type", required = false) String webhookEventType,
            @RequestBody String body) {
        try {
            Optional<WebhookRequestService.ProcessableWebhookEvent> processableOpt =
                    webhookRequestService.resolve(eventSlug, signature, webhookEventType, body);
            if (processableOpt.isEmpty()) {
                return ResponseEntity.ok().build();
            }

            WebhookRequestService.ProcessableWebhookEvent processable = processableOpt.get();
            MDC.put("workspaceId", processable.workspaceId());
            MDC.put("eventType", processable.eventType().name());

            if (TransactionSynchronizationManager.isSynchronizationActive()) {
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        executionService.processWebhookAsync(
                                processable.workspaceId(), processable.eventType(), processable.body());
                    }
                });
            } else {
                executionService.processWebhookAsync(
                        processable.workspaceId(), processable.eventType(), processable.body());
            }

            return ResponseEntity.ok().build();
        } finally {
            MDC.remove("workspaceId");
            MDC.remove("eventType");
        }
    }
}
