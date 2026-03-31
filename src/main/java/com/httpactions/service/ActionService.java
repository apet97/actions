package com.httpactions.service;

import com.httpactions.config.AddonConfig;
import com.httpactions.config.GsonProvider;
import com.httpactions.model.dto.ActionRequest;
import com.httpactions.model.dto.ActionResponse;
import com.httpactions.model.dto.ExecutionCondition;
import com.httpactions.model.dto.SuccessCondition;
import com.httpactions.model.entity.Action;
import com.httpactions.repository.ActionRepository;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.util.*;
import java.util.Locale;
import java.util.regex.Pattern;

@Service
public class ActionService {
    private static final int MAX_ACTIONS_PER_WORKSPACE = 50;
    private static final Pattern ACTION_NAME_PATTERN = Pattern.compile("^[A-Za-z0-9\\- ]+$");

    private static final Set<String> VALID_EXECUTION_OPERATORS = Set.of(
            "equals", "not_equals", "contains", "gt", "lt", "exists", "not_exists");
    private static final Set<String> VALID_SUCCESS_OPERATORS = Set.of(
            "status_range", "body_contains", "body_not_contains");

    private static final Set<String> BLOCKED_HEADER_NAMES = Set.of(
            "host", "transfer-encoding", "connection", "content-length", "upgrade");

    private final ActionRepository actionRepository;
    private final TokenService tokenService;
    private final AddonConfig addonConfig;

    public ActionService(ActionRepository actionRepository, TokenService tokenService,
                         AddonConfig addonConfig) {
        this.actionRepository = actionRepository;
        this.tokenService = tokenService;
        this.addonConfig = addonConfig;
    }

    public List<ActionResponse> listActions(String workspaceId) {
        return actionRepository.findByWorkspaceId(workspaceId).stream()
                .map(this::toResponse)
                .toList();
    }

    public Optional<ActionResponse> getAction(String workspaceId, UUID actionId) {
        return actionRepository.findByIdAndWorkspaceId(actionId, workspaceId)
                .map(this::toResponse);
    }

    @Transactional
    public ActionResponse createAction(String workspaceId, ActionRequest request) {
        // Advisory lock prevents race condition on count-then-insert
        actionRepository.acquireWorkspaceLock(workspaceId);
        long count = actionRepository.countByWorkspaceId(workspaceId);
        if (count >= MAX_ACTIONS_PER_WORKSPACE) {
            throw new IllegalArgumentException("Maximum " + MAX_ACTIONS_PER_WORKSPACE + " actions per workspace");
        }

        validateRequest(request);
        if (request.getUrlTemplate() != null) {
            validateUrl(request.getUrlTemplate());
        }

        Action action = new Action();
        action.setWorkspaceId(workspaceId);
        applyRequest(action, request);
        action = actionRepository.save(action);
        return toResponse(action);
    }

    @Transactional
    public ActionResponse updateAction(String workspaceId, UUID actionId, ActionRequest request) {
        Action action = actionRepository.findByIdAndWorkspaceId(actionId, workspaceId)
                .orElseThrow(() -> new NoSuchElementException("Action not found"));

        validateRequest(request);
        if (request.getUrlTemplate() != null) {
            validateUrl(request.getUrlTemplate());
        }
        applyRequest(action, request);
        action = actionRepository.save(action);
        return toResponse(action);
    }

    @Transactional
    public void deleteAction(String workspaceId, UUID actionId) {
        Action action = actionRepository.findByIdAndWorkspaceId(actionId, workspaceId)
                .orElseThrow(() -> new NoSuchElementException("Action not found"));
        actionRepository.delete(action);
    }

    public Optional<Action> findActionRaw(String workspaceId, UUID actionId) {
        return actionRepository.findByIdAndWorkspaceId(actionId, workspaceId);
    }

    public boolean actionExistsByName(String workspaceId, String name) {
        return actionRepository.existsByWorkspaceIdAndName(workspaceId, name);
    }

    private void applyRequest(Action action, ActionRequest request) {
        action.setName(request.getName());
        action.setEventType(request.getEventType());
        action.setRetryCount(request.getRetryCount());
        action.setEnabled(request.isEnabled());
        action.setHttpMethod(request.getHttpMethod());
        action.setUrlTemplate(request.getUrlTemplate() == null ? null : request.getUrlTemplate().trim());
        action.setBodyTemplate(request.getBodyTemplate());

        // Encrypt headers
        if (request.getHeaders() != null && !request.getHeaders().isEmpty()) {
            action.setHeadersEnc(tokenService.encrypt(GsonProvider.get().toJson(request.getHeaders())));
        } else {
            action.setHeadersEnc(null);
        }

        // Encrypt signing secret
        if (request.getSigningSecret() != null && !request.getSigningSecret().isBlank()) {
            action.setSigningSecretEnc(tokenService.encrypt(request.getSigningSecret()));
        } else {
            action.setSigningSecretEnc(null);
        }

        action.setSuccessConditions(emptyToNull(request.getSuccessConditions()));
        action.setChainOrder(request.getChainOrder());
        action.setExecutionConditions(emptyToNull(request.getExecutionConditions()));
        if (request.getCronExpression() != null && !request.getCronExpression().isBlank()) {
            try {
                CronExpression.parse(request.getCronExpression().trim());
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException(
                        "Invalid cron expression: " + request.getCronExpression()
                        + ". Spring cron requires 6 fields: second minute hour day-of-month month day-of-week"
                        + " (e.g., '0 */5 * * * *' for every 5 minutes). "
                        + "Note: this is NOT the standard 5-field Unix cron format.");
            }
        }
        action.setCronExpression(request.getCronExpression() == null || request.getCronExpression().isBlank()
                ? null
                : request.getCronExpression().trim());
    }

    private ActionResponse toResponse(Action action) {
        Map<String, String> headers = Map.of();
        if (action.getHeadersEnc() != null) {
            String headersJson = tokenService.decrypt(action.getHeadersEnc());
            headers = headersJson == null
                    ? Map.of()
                    : GsonProvider.get().fromJson(headersJson, GsonProvider.STRING_MAP_TYPE);
        }
        return new ActionResponse(
                action.getId(),
                action.getName(),
                action.getEventType(),
                action.isEnabled(),
                action.getHttpMethod(),
                action.getUrlTemplate(),
                headers == null ? Map.of() : headers,
                action.getBodyTemplate(),
                action.getSigningSecretEnc() != null,
                action.getRetryCount(),
                action.getCreatedAt(),
                action.getUpdatedAt(),
                action.getSuccessConditions(),
                action.getChainOrder(),
                action.getExecutionConditions(),
                action.getCronExpression()
        );
    }

    private void validateRequest(ActionRequest request) {
        if (request.getName() == null || !ACTION_NAME_PATTERN.matcher(request.getName()).matches()
                || !request.getName().equals(request.getName().trim())) {
            throw new IllegalArgumentException("Name may only contain letters, numbers, spaces, and dashes");
        }
        if (request.getHttpMethod() == null) {
            throw new IllegalArgumentException("HTTP method is required");
        }
        if (request.getUrlTemplate() == null || request.getUrlTemplate().isBlank()) {
            throw new IllegalArgumentException("URL is required");
        }
        if (request.getHeaders() != null) {
            Set<String> normalizedNames = new HashSet<>();
            request.getHeaders().forEach((key, value) -> {
                String normalizedKey = key.toLowerCase(Locale.ROOT);
                if (!normalizedNames.add(normalizedKey)) {
                    throw new IllegalArgumentException("Duplicate header names are not allowed");
                }
                if (BLOCKED_HEADER_NAMES.contains(normalizedKey)) {
                    throw new IllegalArgumentException("Header name '" + key + "' is not allowed");
                }
            });
        }

        if (request.getChainOrder() != null && (request.getChainOrder() < 1 || request.getChainOrder() > 100)) {
            throw new IllegalArgumentException("Chain order must be between 1 and 100");
        }
        validateExecutionConditions(request.getExecutionConditions());
        validateSuccessConditions(request.getSuccessConditions());
    }

    private <T> List<T> emptyToNull(List<T> values) {
        return values == null || values.isEmpty() ? null : values;
    }

    private void validateUrl(String urlTemplate) {
        String trimmedUrl = urlTemplate.trim();
        if (trimmedUrl.startsWith("{{")) {
            String suffix = trimmedUrl;
            while (suffix.startsWith("{{")) {
                int end = suffix.indexOf("}}");
                if (end < 0) {
                    throw new IllegalArgumentException("Invalid URL template");
                }
                suffix = suffix.substring(end + 2).trim();
            }
            if (!suffix.isEmpty() && !suffix.startsWith("/") && !suffix.startsWith("?")) {
                throw new IllegalArgumentException("Template-prefixed URLs must continue with '/' or '?' after the variable");
            }
            if (suffix.startsWith("/")) {
                validateAbsoluteHttpUrl("https://placeholder.invalid" + suffix);
            } else if (suffix.startsWith("?")) {
                validateAbsoluteHttpUrl("https://placeholder.invalid/" + suffix);
            }
            return;
        }

        String testUrl = trimmedUrl.replaceAll("\\{\\{.+?}}", "placeholder");
        validateAbsoluteHttpUrl(testUrl);
    }

    private void validateAbsoluteHttpUrl(String candidateUrl) {
        URI parsed;
        try {
            parsed = URI.create(candidateUrl);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("URL must be a valid absolute HTTP or HTTPS URL");
        }

        String scheme = parsed.getScheme();
        String host = parsed.getHost();
        if (scheme == null || host == null || host.isBlank()) {
            throw new IllegalArgumentException("URL must be a valid absolute HTTP or HTTPS URL");
        }

        String normalizedScheme = scheme.toLowerCase(Locale.ROOT);
        if (!"http".equals(normalizedScheme) && !"https".equals(normalizedScheme)) {
            throw new IllegalArgumentException("URL must be a valid absolute HTTP or HTTPS URL");
        }

        if (!addonConfig.getOutbound().isAllowHttp() && !"https".equals(normalizedScheme)) {
            throw new IllegalArgumentException("URL must use HTTPS in production");
        }
    }

    private void validateExecutionConditions(List<ExecutionCondition> conditions) {
        if (conditions == null) return;
        for (ExecutionCondition condition : conditions) {
            if (condition.getField() == null || condition.getField().isBlank()) {
                throw new IllegalArgumentException("Execution condition field is required");
            }
            if (condition.getOperator() == null || condition.getOperator().isBlank()) {
                throw new IllegalArgumentException("Execution condition operator is required");
            }
            if (!VALID_EXECUTION_OPERATORS.contains(condition.getOperator())) {
                throw new IllegalArgumentException("Unknown execution condition operator: " + condition.getOperator()
                        + ". Valid operators: " + VALID_EXECUTION_OPERATORS);
            }
            if (!"exists".equals(condition.getOperator())
                    && !"not_exists".equals(condition.getOperator())
                    && (condition.getValue() == null || condition.getValue().isBlank())) {
                throw new IllegalArgumentException("Execution condition value is required");
            }
        }
    }

    private void validateSuccessConditions(List<SuccessCondition> conditions) {
        if (conditions == null) return;
        for (SuccessCondition condition : conditions) {
            if (condition.getOperator() == null || condition.getOperator().isBlank()) {
                throw new IllegalArgumentException("Success condition operator is required");
            }
            if (!VALID_SUCCESS_OPERATORS.contains(condition.getOperator())) {
                throw new IllegalArgumentException("Unknown success condition operator: " + condition.getOperator()
                        + ". Valid operators: " + VALID_SUCCESS_OPERATORS);
            }
            if (condition.getValue() == null || condition.getValue().isBlank()) {
                throw new IllegalArgumentException("Success condition value is required");
            }
        }
    }
}
