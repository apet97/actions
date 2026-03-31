package com.httpactions.service;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.google.gson.reflect.TypeToken;
import com.httpactions.config.GsonProvider;
import com.httpactions.model.dto.ExecutionCondition;
import com.httpactions.model.dto.SuccessCondition;
import com.httpactions.model.dto.TestResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Evaluates success conditions (response-based) and execution conditions (event-based).
 *
 * <p>Success conditions determine whether an HTTP response should be considered successful,
 * overriding the default 2xx check. Execution conditions determine whether an action should
 * fire at all, based on the incoming webhook event payload.</p>
 *
 * <p>Both condition types use AND logic: all conditions must pass.</p>
 */
@Service
public class ConditionEvaluator {

    private static final Logger log = LoggerFactory.getLogger(ConditionEvaluator.class);

    private static final java.lang.reflect.Type EXECUTION_CONDITIONS_TYPE =
            new TypeToken<List<ExecutionCondition>>() {}.getType();
    private static final java.lang.reflect.Type SUCCESS_CONDITIONS_TYPE =
            new TypeToken<List<SuccessCondition>>() {}.getType();

    // --- Success Conditions (F13) ---

    /**
     * Evaluate success conditions against an HTTP response result.
     *
     * @param conditions success conditions from Action.successConditions, or null
     * @param result         the HTTP response result
     * @return true if all conditions pass (or no conditions defined, falling back to default 2xx check)
     */
    public boolean evaluateSuccessConditions(List<SuccessCondition> conditions, TestResult result) {
        if (conditions == null || conditions.isEmpty()) {
            return result.isSuccess();
        }

        for (SuccessCondition condition : conditions) {
            if (condition == null) {
                log.warn("Malformed success condition: null entry");
                return false;
            }
            String operator = condition.getOperator();
            String value = condition.getValue();
            if (operator == null || operator.isBlank() || value == null || value.isBlank()) {
                log.warn("Malformed success condition: operator/value missing");
                return false;
            }

            boolean passed = evaluateSingleSuccessCondition(operator, value, result);
            if (!passed) {
                log.debug("Success condition failed: operator={}, value={}, status={}",
                        operator, value, result.getResponseStatus());
                return false;
            }
        }

        return true;
    }

    public boolean evaluateSuccessConditions(String conditionsJson, TestResult result) {
        if (conditionsJson == null || conditionsJson.isBlank()) {
            return evaluateSuccessConditions((List<SuccessCondition>) null, result);
        }
        try {
            List<SuccessCondition> conditions = GsonProvider.get().fromJson(conditionsJson, SUCCESS_CONDITIONS_TYPE);
            return evaluateSuccessConditions(conditions, result);
        } catch (Exception e) {
            log.warn("Failed to parse success conditions JSON, treating as failure: {}", e.getMessage());
            return false;
        }
    }

    private boolean evaluateSingleSuccessCondition(String operator, String value, TestResult result) {
        return switch (operator) {
            case "status_range" -> evaluateStatusRange(value, result.getResponseStatus());
            case "body_contains" -> evaluateBodyContains(value, result.getResponseBody());
            case "body_not_contains" -> evaluateBodyNotContains(value, result.getResponseBody());
            default -> {
                log.warn("Unknown success condition operator: {}", operator);
                yield false;
            }
        };
    }

    private boolean evaluateStatusRange(String rangeValue, Integer status) {
        if (status == null || rangeValue == null) return false;

        String[] parts = rangeValue.split("-");
        if (parts.length != 2) {
            log.warn("Invalid status_range format (expected 'min-max'): {}", rangeValue);
            return false;
        }

        try {
            int min = Integer.parseInt(parts[0].trim());
            int max = Integer.parseInt(parts[1].trim());
            return status >= min && status <= max;
        } catch (NumberFormatException e) {
            log.warn("Invalid status_range numbers: {}", rangeValue);
            return false;
        }
    }

    private boolean evaluateBodyContains(String value, String responseBody) {
        if (value == null) return false;
        if (responseBody == null) return false;
        return responseBody.contains(value);
    }

    private boolean evaluateBodyNotContains(String value, String responseBody) {
        if (value == null) return false;
        if (responseBody == null) return true;
        return !responseBody.contains(value);
    }

    // --- Execution Conditions (F18) ---

    /**
     * Evaluate execution conditions against a webhook event payload.
     *
     * @param conditions execution conditions from Action.conditions, or null
     * @param event          the webhook event payload
     * @return true if all conditions pass (or no conditions defined)
     */
    public boolean evaluateExecutionConditions(List<ExecutionCondition> conditions, JsonObject event) {
        if (conditions == null || conditions.isEmpty()) {
            return true;
        }

        for (ExecutionCondition condition : conditions) {
            if (condition == null) {
                log.warn("Malformed execution condition: null entry");
                return false;
            }

            String field = condition.getField();
            String operator = condition.getOperator();
            String value = condition.getValue();
            if (field == null || field.isBlank() || operator == null || operator.isBlank()) {
                log.warn("Malformed execution condition: field/operator missing");
                return false;
            }

            boolean passed = evaluateSingleExecutionCondition(field, operator, value, event);
            if (!passed) {
                log.debug("Execution condition failed: field={}, operator={}, value={}", field, operator, value);
                return false;
            }
        }

        return true;
    }

    public boolean evaluateExecutionConditions(String conditionsJson, JsonObject event) {
        if (conditionsJson == null || conditionsJson.isBlank()) {
            return evaluateExecutionConditions((List<ExecutionCondition>) null, event);
        }
        try {
            List<ExecutionCondition> conditions = GsonProvider.get().fromJson(conditionsJson, EXECUTION_CONDITIONS_TYPE);
            return evaluateExecutionConditions(conditions, event);
        } catch (Exception e) {
            log.warn("Failed to parse execution conditions JSON, treating as failure: {}", e.getMessage());
            return false;
        }
    }

    private boolean evaluateSingleExecutionCondition(String field, String operator, String value, JsonObject event) {
        // exists/not_exists operators only need the field
        if ("exists".equals(operator)) {
            return resolveField(event, field) != null;
        }
        if ("not_exists".equals(operator)) {
            return resolveField(event, field) == null;
        }

        // All other operators need the resolved field value
        String fieldValue = resolveFieldAsString(event, field);

        return switch (operator) {
            case "equals" -> value != null && value.equals(fieldValue);
            case "not_equals" -> fieldValue != null && value != null && !fieldValue.equals(value);
            case "contains" -> fieldValue != null && value != null && fieldValue.contains(value);
            case "gt" -> isNumericComparable(fieldValue, value) && compareNumeric(fieldValue, value) > 0;
            case "lt" -> isNumericComparable(fieldValue, value) && compareNumeric(fieldValue, value) < 0;
            default -> {
                log.warn("Unknown execution condition operator: {}", operator);
                yield false;
            }
        };
    }

    // --- JSON traversal (delegated to JsonPathResolver) ---

    /**
     * Resolve a dot-notation path in a JsonObject, returning the raw JsonElement.
     * Returns null if the path does not exist or leads to a JSON null.
     */
    private JsonElement resolveField(JsonObject root, String path) {
        return JsonPathResolver.resolve(root, path);
    }

    /**
     * Resolve a field path and convert to a string representation.
     * Returns {@code null} (not empty string) for missing/null fields, preserving
     * null-aware comparison semantics in execution condition operators.
     */
    private String resolveFieldAsString(JsonObject root, String path) {
        JsonElement element = JsonPathResolver.resolve(root, path);
        if (element == null) return null;

        if (element.isJsonPrimitive()) {
            JsonPrimitive prim = element.getAsJsonPrimitive();
            if (prim.isString()) return prim.getAsString();
            return prim.toString();
        }

        return element.toString();
    }

    private boolean isNumericComparable(String fieldValue, String conditionValue) {
        if (fieldValue == null || conditionValue == null) return false;
        try {
            Double.parseDouble(fieldValue);
            Double.parseDouble(conditionValue);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private int compareNumeric(String fieldValue, String conditionValue) {
        double a = Double.parseDouble(fieldValue);
        double b = Double.parseDouble(conditionValue);
        return Double.compare(a, b);
    }
}
