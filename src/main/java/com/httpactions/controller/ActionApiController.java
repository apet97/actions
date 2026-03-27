package com.httpactions.controller;

import com.google.gson.reflect.TypeToken;
import com.httpactions.config.GsonProvider;
import com.httpactions.model.dto.ActionRequest;
import com.httpactions.model.dto.ActionResponse;
import com.httpactions.model.dto.ExecutionLogResponse;
import com.httpactions.model.dto.ExportAction;
import com.httpactions.model.dto.TestResult;
import com.httpactions.model.entity.Action;
import com.httpactions.model.enums.EventType;
import com.httpactions.service.ActionService;
import com.httpactions.service.ActionTemplateService;
import com.httpactions.service.ActionTemplateService.ActionTemplate;
import com.httpactions.service.EventSchemaRegistry;
import com.httpactions.service.EventSchemaRegistry.EventVariable;
import com.httpactions.service.ExecutionService;
import com.httpactions.service.LogService;
import com.httpactions.service.TokenService;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Valid;
import jakarta.validation.Validator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import jakarta.servlet.http.HttpServletRequest;
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
    private final TokenService tokenService;
    private final EventSchemaRegistry eventSchemaRegistry;
    private final ActionTemplateService actionTemplateService;
    private final Validator validator;

    public ActionApiController(ActionService actionService, ExecutionService executionService,
                               LogService logService, TokenService tokenService,
                               EventSchemaRegistry eventSchemaRegistry,
                               ActionTemplateService actionTemplateService,
                               Validator validator) {
        this.actionService = actionService;
        this.executionService = executionService;
        this.logService = logService;
        this.tokenService = tokenService;
        this.eventSchemaRegistry = eventSchemaRegistry;
        this.actionTemplateService = actionTemplateService;
        this.validator = validator;
    }

    @GetMapping("/actions")
    public ResponseEntity<List<ActionResponse>> listActions(
            @RequestHeader("X-Addon-Token") String token) {

        String workspaceId = extractWorkspaceId(token);
        if (workspaceId == null) return ResponseEntity.status(401).build();

        return ResponseEntity.ok(actionService.listActions(workspaceId));
    }

    @PostMapping("/actions")
    public ResponseEntity<?> createAction(
            @RequestHeader("X-Addon-Token") String token,
            @Valid @RequestBody ActionRequest request) {

        String workspaceId = extractWorkspaceId(token);
        if (workspaceId == null) return ResponseEntity.status(401).build();

        ActionResponse response = actionService.createAction(workspaceId, request);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/actions/{id}")
    public ResponseEntity<?> updateAction(
            @RequestHeader("X-Addon-Token") String token,
            @PathVariable UUID id,
            @Valid @RequestBody ActionRequest request) {

        String workspaceId = extractWorkspaceId(token);
        if (workspaceId == null) return ResponseEntity.status(401).build();

        ActionResponse response = actionService.updateAction(workspaceId, id, request);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/actions/{id}")
    public ResponseEntity<Void> deleteAction(
            @RequestHeader("X-Addon-Token") String token,
            @PathVariable UUID id) {

        String workspaceId = extractWorkspaceId(token);
        if (workspaceId == null) return ResponseEntity.status(401).build();

        actionService.deleteAction(workspaceId, id);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/actions/{id}/test")
    public ResponseEntity<?> testAction(
            @RequestHeader("X-Addon-Token") String token,
            @PathVariable UUID id,
            @RequestBody(required = false) String samplePayload) {

        String workspaceId = extractWorkspaceId(token);
        if (workspaceId == null) return ResponseEntity.status(401).build();

        Optional<Action> actionOpt = actionService.findActionRaw(workspaceId, id);
        if (actionOpt.isEmpty()) {
            throw new NoSuchElementException("Action not found");
        }

        // Use provided sample or generate a minimal test payload
        if (samplePayload == null || samplePayload.isBlank()) {
            samplePayload = generateSamplePayload(actionOpt.get().getEventType());
        }

        TestResult result = executionService.testAction(actionOpt.get(), samplePayload);
        return ResponseEntity.ok(result);
    }

    @GetMapping("/actions/{id}/logs")
    public ResponseEntity<Page<ExecutionLogResponse>> getActionLogs(
            @RequestHeader("X-Addon-Token") String token,
            @PathVariable UUID id,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        String workspaceId = extractWorkspaceId(token);
        if (workspaceId == null) return ResponseEntity.status(401).build();

        // Verify action belongs to workspace
        if (actionService.getAction(workspaceId, id).isEmpty()) {
            throw new NoSuchElementException("Action not found");
        }

        return ResponseEntity.ok(logService.getLogsForAction(id, Math.max(0, page), normalizePageSize(size)));
    }

    @GetMapping("/logs")
    public ResponseEntity<Page<ExecutionLogResponse>> getWorkspaceLogs(
            @RequestHeader("X-Addon-Token") String token,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        String workspaceId = extractWorkspaceId(token);
        if (workspaceId == null) return ResponseEntity.status(401).build();

        return ResponseEntity.ok(logService.getLogsForWorkspace(
                workspaceId, Math.max(0, page), normalizePageSize(size)));
    }

    @GetMapping("/events")
    public ResponseEntity<List<Map<String, Object>>> listEvents(
            @RequestHeader("X-Addon-Token") String token) {

        String workspaceId = extractWorkspaceId(token);
        if (workspaceId == null) return ResponseEntity.status(401).build();

        List<Map<String, Object>> events = new ArrayList<>();
        for (EventType et : EventType.values()) {
            List<EventVariable> variables = eventSchemaRegistry.getSchema(et);
            List<Map<String, String>> varList = variables.stream()
                    .map(v -> Map.of(
                            "path", v.getPath(),
                            "type", v.getType(),
                            "description", v.getDescription()
                    ))
                    .toList();
            events.add(Map.of(
                    "name", et.name(),
                    "slug", et.getSlug(),
                    "label", et.name().replace("_", " "),
                    "variables", varList
            ));
        }
        return ResponseEntity.ok(events);
    }

    @GetMapping("/actions/export")
    public ResponseEntity<List<ExportAction>> exportActions(
            @RequestHeader("X-Addon-Token") String token) {

        String workspaceId = extractWorkspaceId(token);
        if (workspaceId == null) return ResponseEntity.status(401).build();

        List<ActionResponse> actions = actionService.listActions(workspaceId);
        List<ExportAction> exports = actions.stream().map(a -> {
            ExportAction ea = new ExportAction();
            ea.setName(a.getName());
            ea.setEventType(a.getEventType());
            ea.setHttpMethod(a.getHttpMethod());
            ea.setUrlTemplate(a.getUrlTemplate());
            ea.setBodyTemplate(a.getBodyTemplate());
            ea.setRetryCount(a.getRetryCount());
            ea.setEnabled(a.isEnabled());
            ea.setSuccessConditions(a.getSuccessConditions());
            ea.setChainOrder(a.getChainOrder());
            ea.setExecutionConditions(a.getExecutionConditions());
            ea.setCronExpression(a.getCronExpression());
            // Secrets (headersEnc, signingSecretEnc) are REDACTED — not included in ExportAction
            return ea;
        }).toList();
        return ResponseEntity.ok(exports);
    }

    @PostMapping("/actions/import")
    public ResponseEntity<?> importActions(
            @RequestHeader("X-Addon-Token") String token,
            @RequestBody String body) {

        String workspaceId = extractWorkspaceId(token);
        if (workspaceId == null) return ResponseEntity.status(401).build();

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
            if (ea.getEventType() == null || ea.getHttpMethod() == null
                    || ea.getUrlTemplate() == null || ea.getUrlTemplate().isBlank()) {
                skipped.add(ea.getName());
                continue;
            }

            // Skip duplicates by name
            if (actionService.actionExistsByName(workspaceId, ea.getName())) {
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
                ActionResponse response = actionService.createAction(workspaceId, request);
                created.add(response);
            } catch (Exception e) {
                log.warn("Failed to import action '{}': {}", ea.getName(), e.getMessage());
                skipped.add(ea.getName());
            }
        }

        return ResponseEntity.ok(Map.of(
                "created", created,
                "skipped", skipped,
                "warning", "Headers and signing secrets are not included in exports and must be re-configured after import."
        ));
    }

    @GetMapping("/templates")
    public ResponseEntity<List<ActionTemplate>> listTemplates(
            @RequestHeader("X-Addon-Token") String token) {

        String workspaceId = extractWorkspaceId(token);
        if (workspaceId == null) return ResponseEntity.status(401).build();

        return ResponseEntity.ok(actionTemplateService.getTemplates());
    }

    @SuppressWarnings("unchecked")
    private String extractWorkspaceId(String token) {
        // Reuse pre-verified claims from RateLimitFilter to avoid double RSA verification
        ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attrs != null) {
            Object cached = attrs.getRequest().getAttribute("verifiedClaims");
            if (cached instanceof Map<?, ?> claims) {
                Object wsId = claims.get("workspaceId");
                if (wsId instanceof String s) return s;
            }
        }
        // Fallback: verify directly (e.g., when filter is bypassed in tests)
        Map<String, Object> claims = tokenService.verifyAndParseClaims(token);
        if (claims == null) return null;
        return (String) claims.get("workspaceId");
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
