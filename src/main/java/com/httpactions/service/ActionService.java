package com.httpactions.service;

import com.google.gson.reflect.TypeToken;
import com.httpactions.config.AddonConfig;
import com.httpactions.config.GsonProvider;
import com.httpactions.model.dto.ActionRequest;
import com.httpactions.model.dto.ActionResponse;
import com.httpactions.model.dto.ExecutionCondition;
import com.httpactions.model.dto.SuccessCondition;
import com.httpactions.model.entity.Action;
import com.httpactions.model.enums.EventType;
import com.httpactions.repository.ActionRepository;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Type;
import java.util.*;

@Service
public class ActionService {

    private static final Type HEADERS_TYPE = new TypeToken<Map<String, String>>() {}.getType();
    private static final int MAX_ACTIONS_PER_WORKSPACE = 50;
    private static final String ACTION_NAME_PATTERN = "^[A-Za-z0-9\\- ]+$";

    private final ActionRepository actionRepository;
    private final TokenService tokenService;
    private final AddonConfig addonConfig;

    public ActionService(ActionRepository actionRepository, TokenService tokenService, AddonConfig addonConfig) {
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
        // Validate max actions per workspace
        long count = actionRepository.countByWorkspaceId(workspaceId);
        if (count >= MAX_ACTIONS_PER_WORKSPACE) {
            throw new IllegalStateException("Maximum " + MAX_ACTIONS_PER_WORKSPACE + " actions per workspace");
        }

        validateRequest(request);
        // Validate URL scheme
        validateUrl(request.getUrlTemplate());

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
        validateUrl(request.getUrlTemplate());
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

    private void validateUrl(String urlTemplate) {
        // Strip template variables for validation
        String testUrl = urlTemplate.replaceAll("\\{\\{.+?}}", "placeholder");
        if (!addonConfig.getOutbound().isAllowHttp() && !testUrl.toLowerCase().startsWith("https://")) {
            throw new IllegalArgumentException("URL must use HTTPS in production");
        }
    }

    private void applyRequest(Action action, ActionRequest request) {
        action.setName(request.getName());
        action.setEventType(request.getEventType());
        action.setHttpMethod(request.getHttpMethod());
        action.setUrlTemplate(request.getUrlTemplate());
        action.setBodyTemplate(request.getBodyTemplate());
        action.setRetryCount(request.getRetryCount());
        action.setEnabled(request.isEnabled());

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

        // P1/P2 fields
        if (request.getSuccessConditions() != null && !request.getSuccessConditions().isEmpty()) {
            action.setSuccessConditions(GsonProvider.get().toJson(request.getSuccessConditions()));
        } else {
            action.setSuccessConditions(null);
        }
        action.setChainOrder(request.getChainOrder());
        if (request.getExecutionConditions() != null && !request.getExecutionConditions().isEmpty()) {
            action.setConditions(GsonProvider.get().toJson(request.getExecutionConditions()));
        } else {
            action.setConditions(null);
        }
        if (request.getCronExpression() != null && !request.getCronExpression().isBlank()) {
            try {
                CronExpression.parse(request.getCronExpression());
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException(
                        "Invalid cron expression: " + request.getCronExpression()
                        + ". Spring cron requires 6 fields: second minute hour day-of-month month day-of-week"
                        + " (e.g., '0 */5 * * * *' for every 5 minutes). "
                        + "Note: this is NOT the standard 5-field Unix cron format.");
            }
        }
        action.setCronExpression(request.getCronExpression());
    }

    private ActionResponse toResponse(Action action) {
        ActionResponse response = new ActionResponse();
        response.setId(action.getId());
        response.setName(action.getName());
        response.setEventType(action.getEventType());
        response.setEnabled(action.isEnabled());
        response.setHttpMethod(action.getHttpMethod());
        response.setUrlTemplate(action.getUrlTemplate());
        response.setBodyTemplate(action.getBodyTemplate());
        response.setHasSigning(action.getSigningSecretEnc() != null);
        response.setRetryCount(action.getRetryCount());
        response.setCreatedAt(action.getCreatedAt());
        response.setUpdatedAt(action.getUpdatedAt());

        // Decrypt headers
        if (action.getHeadersEnc() != null) {
            String headersJson = tokenService.decrypt(action.getHeadersEnc());
            Map<String, String> headers = headersJson == null
                    ? Map.of()
                    : GsonProvider.get().fromJson(headersJson, HEADERS_TYPE);
            response.setHeaders(headers != null ? headers : Map.of());
        } else {
            response.setHeaders(Map.of());
        }

        // P1/P2 fields
        if (action.getSuccessConditions() != null) {
            response.setSuccessConditions(GsonProvider.get().fromJson(action.getSuccessConditions(),
                    new TypeToken<List<SuccessCondition>>() {}.getType()));
        }
        response.setChainOrder(action.getChainOrder());
        if (action.getConditions() != null) {
            response.setExecutionConditions(GsonProvider.get().fromJson(action.getConditions(),
                    new TypeToken<List<ExecutionCondition>>() {}.getType()));
        }
        response.setCronExpression(action.getCronExpression());

        return response;
    }

    private void validateRequest(ActionRequest request) {
        if (request.getName() == null || !request.getName().matches(ACTION_NAME_PATTERN)) {
            throw new IllegalArgumentException("Name may only contain letters, numbers, spaces, and dashes");
        }
        if (request.getHeaders() != null) {
            request.getHeaders().forEach((key, value) -> {
                if (key == null || key.isBlank() || key.length() > 128) {
                    throw new IllegalArgumentException("Header names must be 1-128 characters");
                }
                if (value == null || value.length() > 4096) {
                    throw new IllegalArgumentException("Header values must be 0-4096 characters");
                }
            });
        }
        if (request.getChainOrder() != null && (request.getChainOrder() < 1 || request.getChainOrder() > 100)) {
            throw new IllegalArgumentException("Chain order must be between 1 and 100");
        }
        validateExecutionConditions(request.getExecutionConditions());
        validateSuccessConditions(request.getSuccessConditions());
    }

    private void validateExecutionConditions(List<ExecutionCondition> conditions) {
        if (conditions == null) {
            return;
        }
        for (ExecutionCondition condition : conditions) {
            if (condition.getField() == null || condition.getField().isBlank()) {
                throw new IllegalArgumentException("Execution condition field is required");
            }
            if (condition.getOperator() == null || condition.getOperator().isBlank()) {
                throw new IllegalArgumentException("Execution condition operator is required");
            }
            if (!"exists".equals(condition.getOperator())
                    && !"not_exists".equals(condition.getOperator())
                    && (condition.getValue() == null || condition.getValue().isBlank())) {
                throw new IllegalArgumentException("Execution condition value is required");
            }
        }
    }

    private void validateSuccessConditions(List<SuccessCondition> conditions) {
        if (conditions == null) {
            return;
        }
        for (SuccessCondition condition : conditions) {
            if (condition.getOperator() == null || condition.getOperator().isBlank()) {
                throw new IllegalArgumentException("Success condition operator is required");
            }
            if (condition.getValue() == null || condition.getValue().isBlank()) {
                throw new IllegalArgumentException("Success condition value is required");
            }
        }
    }
}
