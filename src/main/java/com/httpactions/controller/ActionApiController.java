package com.httpactions.controller;

import com.google.gson.reflect.TypeToken;
import com.httpactions.config.GsonProvider;
import com.httpactions.model.dto.ActionImportResponse;
import com.httpactions.model.dto.ActionRequest;
import com.httpactions.model.dto.ActionResponse;
import com.httpactions.model.dto.EventTypeResponse;
import com.httpactions.model.dto.EventVariableResponse;
import com.httpactions.model.dto.ExecutionLogResponse;
import com.httpactions.model.dto.ExportAction;
import com.httpactions.model.dto.PageResponse;
import com.httpactions.model.dto.TestResult;
import com.httpactions.model.dto.VerifiedAddonContext;
import com.httpactions.model.entity.Action;
import com.httpactions.model.enums.EventType;
import com.httpactions.service.ActionService;
import com.httpactions.service.ActionTemplateService;
import com.httpactions.service.ActionTemplateService.ActionTemplate;
import com.httpactions.service.EventSchemaRegistry;
import com.httpactions.service.EventSchemaRegistry.EventVariable;
import com.httpactions.service.ExecutionService;
import com.httpactions.service.LogService;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Valid;
import jakarta.validation.Validator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.lang.reflect.Type;
import java.time.LocalDate;
import java.util.*;

@RestController
@RequestMapping("/api")
public class ActionApiController {

    private static final Logger log = LoggerFactory.getLogger(ActionApiController.class);
    private static final Type EXPORT_LIST_TYPE = new TypeToken<List<ExportAction>>() {}.getType();

    private final ActionService actionService;
    private final ExecutionService executionService;
    private final LogService logService;
    private final EventSchemaRegistry eventSchemaRegistry;
    private final ActionTemplateService actionTemplateService;
    private final Validator validator;

    public ActionApiController(ActionService actionService, ExecutionService executionService,
                               LogService logService,
                               EventSchemaRegistry eventSchemaRegistry,
                               ActionTemplateService actionTemplateService,
                               Validator validator) {
        this.actionService = actionService;
        this.executionService = executionService;
        this.logService = logService;
        this.eventSchemaRegistry = eventSchemaRegistry;
        this.actionTemplateService = actionTemplateService;
        this.validator = validator;
    }

    @GetMapping("/actions")
    public ResponseEntity<List<ActionResponse>> listActions(VerifiedAddonContext addonContext) {
        return ResponseEntity.ok(actionService.listActions(addonContext.workspaceId()));
    }

    @PostMapping("/actions")
    public ResponseEntity<ActionResponse> createAction(
            VerifiedAddonContext addonContext,
            @Valid @RequestBody ActionRequest request) {
        ActionResponse response = actionService.createAction(addonContext.workspaceId(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PutMapping("/actions/{id}")
    public ResponseEntity<ActionResponse> updateAction(
            VerifiedAddonContext addonContext,
            @PathVariable UUID id,
            @Valid @RequestBody ActionRequest request) {
        ActionResponse response = actionService.updateAction(addonContext.workspaceId(), id, request);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/actions/{id}")
    public ResponseEntity<Void> deleteAction(
            VerifiedAddonContext addonContext,
            @PathVariable UUID id) {
        actionService.deleteAction(addonContext.workspaceId(), id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/actions/{id}/test")
    public ResponseEntity<TestResult> testAction(
            VerifiedAddonContext addonContext,
            @PathVariable UUID id,
            @RequestBody(required = false) String samplePayload) {
        Optional<Action> actionOpt = actionService.findActionRaw(addonContext.workspaceId(), id);
        if (actionOpt.isEmpty()) {
            throw new NoSuchElementException("Action not found");
        }

        if (samplePayload == null || samplePayload.isBlank()) {
            samplePayload = generateSamplePayload(actionOpt.get().getEventType());
        }

        TestResult result = executionService.testAction(actionOpt.get(), samplePayload);
        return ResponseEntity.ok(result);
    }

    @GetMapping("/actions/{id}/logs")
    public ResponseEntity<PageResponse<ExecutionLogResponse>> getActionLogs(
            VerifiedAddonContext addonContext,
            @PathVariable UUID id,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        if (actionService.getAction(addonContext.workspaceId(), id).isEmpty()) {
            throw new NoSuchElementException("Action not found");
        }

        return ResponseEntity.ok(PageResponse.from(
                logService.getLogsForAction(id, Math.max(0, page), normalizePageSize(size))));
    }

    @GetMapping("/logs")
    public ResponseEntity<PageResponse<ExecutionLogResponse>> getWorkspaceLogs(
            VerifiedAddonContext addonContext,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(PageResponse.from(logService.getLogsForWorkspace(
                addonContext.workspaceId(), Math.max(0, page), normalizePageSize(size))));
    }

    @GetMapping("/events")
    public ResponseEntity<List<EventTypeResponse>> listEvents(VerifiedAddonContext addonContext) {
        List<EventTypeResponse> events = new ArrayList<>();
        for (EventType et : EventType.values()) {
            List<EventVariable> variables = eventSchemaRegistry.getSchema(et);
            List<EventVariableResponse> varList = variables.stream()
                    .map(v -> new EventVariableResponse(v.getPath(), v.getType(), v.getDescription()))
                    .toList();
            events.add(new EventTypeResponse(
                    et.name(),
                    et.getSlug(),
                    et.name().replace("_", " "),
                    varList
            ));
        }
        return ResponseEntity.ok(events);
    }

    @GetMapping("/actions/export")
    public ResponseEntity<List<ExportAction>> exportActions(
            VerifiedAddonContext addonContext) {
        List<ActionResponse> actions = actionService.listActions(addonContext.workspaceId());
        List<ExportAction> exports = actions.stream().map(a -> {
            ExportAction ea = new ExportAction();
            ea.setName(a.name());
            ea.setEventType(a.eventType());
            ea.setHttpMethod(a.httpMethod());
            ea.setUrlTemplate(a.urlTemplate());
            ea.setBodyTemplate(a.bodyTemplate());
            ea.setRetryCount(a.retryCount());
            ea.setEnabled(a.enabled());
            ea.setSuccessConditions(a.successConditions());
            ea.setChainOrder(a.chainOrder());
            ea.setExecutionConditions(a.executionConditions());
            ea.setCronExpression(a.cronExpression());
            // Secrets (headersEnc, signingSecretEnc) are REDACTED — not included in ExportAction
            return ea;
        }).toList();
        return ResponseEntity.ok(exports);
    }

    @PostMapping("/actions/import")
    public ResponseEntity<ActionImportResponse> importActions(
            VerifiedAddonContext addonContext,
            @RequestBody String body) {
        List<ExportAction> imports;
        try {
            imports = GsonProvider.get().fromJson(body, EXPORT_LIST_TYPE);
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid JSON array");
        }
        if (imports == null || imports.isEmpty()) {
            throw new IllegalArgumentException("Empty import list");
        }
        if (imports.size() > 50) {
            throw new IllegalArgumentException("Import limited to 50 actions");
        }

        List<ActionResponse> created = new ArrayList<>();
        List<String> skipped = new ArrayList<>();

        for (ExportAction ea : imports) {
            if (ea.getName() == null || ea.getName().isBlank()) {
                skipped.add("(unnamed action)");
                continue;
            }
            if (ea.getEventType() == null) {
                skipped.add(ea.getName());
                continue;
            }
            if (ea.getHttpMethod() == null || ea.getUrlTemplate() == null || ea.getUrlTemplate().isBlank()) {
                skipped.add(ea.getName());
                continue;
            }

            // Skip duplicates by name
            if (actionService.actionExistsByName(addonContext.workspaceId(), ea.getName())) {
                skipped.add(ea.getName());
                continue;
            }

            ActionRequest request = new ActionRequest();
            request.setName(ea.getName());
            request.setEventType(ea.getEventType());
            request.setHttpMethod(ea.getHttpMethod());
            request.setUrlTemplate(ea.getUrlTemplate());
            request.setBodyTemplate(ea.getBodyTemplate());
            request.setRetryCount(ea.getRetryCount());
            request.setEnabled(ea.isEnabled());
            request.setSuccessConditions(ea.getSuccessConditions());
            request.setChainOrder(ea.getChainOrder());
            request.setExecutionConditions(ea.getExecutionConditions());
            request.setCronExpression(ea.getCronExpression());

            // Validate imported request the same way @Valid does on the create endpoint
            Set<ConstraintViolation<ActionRequest>> violations = validator.validate(request);
            if (!violations.isEmpty()) {
                log.warn("Validation failed for imported action '{}': {}", ea.getName(),
                        violations.iterator().next().getMessage());
                skipped.add(ea.getName());
                continue;
            }

            try {
                ActionResponse response = actionService.createAction(addonContext.workspaceId(), request);
                created.add(response);
            } catch (Exception e) {
                log.warn("Failed to import action '{}': {}", ea.getName(), e.getMessage());
                skipped.add(ea.getName());
            }
        }

        return ResponseEntity.ok(new ActionImportResponse(
                created,
                skipped,
                "Headers and signing secrets are not included in exports and must be re-configured after import."
        ));
    }

    @GetMapping("/templates")
    public ResponseEntity<List<ActionTemplate>> listTemplates(VerifiedAddonContext addonContext) {
        return ResponseEntity.ok(actionTemplateService.getTemplates());
    }

    private int normalizePageSize(int size) {
        return Math.max(1, Math.min(size, 100));
    }

    // L3: use dynamic dates instead of hardcoded values
    private String generateSamplePayload(EventType eventType) {
        String today = LocalDate.now().toString();
        return switch (eventType) {
            case NEW_TIME_ENTRY, TIME_ENTRY_UPDATED, TIME_ENTRY_DELETED ->
                    "{\"id\":\"sample-te-id\",\"description\":\"Sample time entry\",\"userId\":\"sample-user-id\"," +
                    "\"timeInterval\":{\"start\":\"" + today + "T09:00:00Z\",\"end\":\"" + today + "T17:00:00Z\"," +
                    "\"duration\":\"PT8H\"},\"projectId\":\"sample-project-id\",\"tags\":[{\"id\":\"tag1\",\"name\":\"billable\"}]}";
            case NEW_TIMER_STARTED, TIMER_STOPPED ->
                    "{\"id\":\"sample-te-id\",\"description\":\"Timer event\",\"userId\":\"sample-user-id\"," +
                    "\"timeInterval\":{\"start\":\"" + today + "T09:00:00Z\",\"end\":null,\"duration\":null}}";
            case NEW_PROJECT -> """
                    {"id":"sample-project-id","name":"Sample Project","clientId":"sample-client-id",\
                    "workspaceId":"sample-ws-id","color":"#0066FF","billable":true}""";
            case NEW_CLIENT -> """
                    {"id":"sample-client-id","name":"Sample Client","workspaceId":"sample-ws-id",\
                    "email":"client@example.com"}""";
            case NEW_INVOICE -> """
                    {"id":"sample-invoice-id","number":"INV-001","clientId":"sample-client-id",\
                    "status":"UNSENT","total":10500}""";
            case USER_JOINED_WORKSPACE -> """
                    {"id":"sample-user-id","email":"user@example.com","name":"Sample User",\
                    "status":"ACTIVE"}""";
            case NEW_TASK -> """
                    {"id":"sample-task-id","name":"Sample Task","projectId":"sample-project-id",\
                    "status":"ACTIVE","estimate":"PT2H"}""";
        };
    }
}
