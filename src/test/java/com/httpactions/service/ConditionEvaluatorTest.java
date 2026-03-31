package com.httpactions.service;

import com.google.gson.JsonObject;
import com.httpactions.model.dto.TestResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ConditionEvaluatorTest {

    private ConditionEvaluator evaluator;

    @BeforeEach
    void setUp() {
        evaluator = new ConditionEvaluator();
    }

    // ---------------------------------------------------------------
    // Execution Conditions  (evaluateExecutionConditions)
    // ---------------------------------------------------------------
    @Nested
    @DisplayName("Execution conditions")
    class ExecutionConditions {

        @Test
        void evaluateExecution_equals_match_returnsTrue() {
            String conditions = "[{\"field\":\"description\",\"operator\":\"equals\",\"value\":\"test\"}]";
            JsonObject event = new JsonObject();
            event.addProperty("description", "test");

            assertTrue(evaluator.evaluateExecutionConditions(conditions, event));
        }

        @Test
        void evaluateExecution_equals_noMatch_returnsFalse() {
            String conditions = "[{\"field\":\"description\",\"operator\":\"equals\",\"value\":\"test\"}]";
            JsonObject event = new JsonObject();
            event.addProperty("description", "something else");

            assertFalse(evaluator.evaluateExecutionConditions(conditions, event));
        }

        @Test
        void evaluateExecution_notEquals_works() {
            String conditions = "[{\"field\":\"status\",\"operator\":\"not_equals\",\"value\":\"CLOSED\"}]";
            JsonObject event = new JsonObject();
            event.addProperty("status", "OPEN");

            assertTrue(evaluator.evaluateExecutionConditions(conditions, event));
        }

        @Test
        void evaluateExecution_contains_substringMatch() {
            String conditions = "[{\"field\":\"description\",\"operator\":\"contains\",\"value\":\"urgent\"}]";
            JsonObject event = new JsonObject();
            event.addProperty("description", "This is an urgent task");

            assertTrue(evaluator.evaluateExecutionConditions(conditions, event));
        }

        @Test
        void evaluateExecution_gt_numericComparison() {
            String conditions = "[{\"field\":\"amount\",\"operator\":\"gt\",\"value\":\"100\"}]";
            JsonObject event = new JsonObject();
            event.addProperty("amount", 250);

            assertTrue(evaluator.evaluateExecutionConditions(conditions, event));
        }

        @Test
        void evaluateExecution_lt_numericComparison() {
            String conditions = "[{\"field\":\"amount\",\"operator\":\"lt\",\"value\":\"100\"}]";
            JsonObject event = new JsonObject();
            event.addProperty("amount", 50);

            assertTrue(evaluator.evaluateExecutionConditions(conditions, event));
        }

        @Test
        void evaluateExecution_exists_fieldPresent_returnsTrue() {
            String conditions = "[{\"field\":\"projectId\",\"operator\":\"exists\"}]";
            JsonObject event = new JsonObject();
            event.addProperty("projectId", "abc-123");

            assertTrue(evaluator.evaluateExecutionConditions(conditions, event));
        }

        @Test
        void evaluateExecution_exists_fieldMissing_returnsFalse() {
            String conditions = "[{\"field\":\"projectId\",\"operator\":\"exists\"}]";
            JsonObject event = new JsonObject();
            event.addProperty("description", "no project here");

            assertFalse(evaluator.evaluateExecutionConditions(conditions, event));
        }

        @Test
        void evaluateExecution_notExists_works() {
            String conditions = "[{\"field\":\"deletedAt\",\"operator\":\"not_exists\"}]";
            JsonObject event = new JsonObject();
            event.addProperty("description", "active entry");

            assertTrue(evaluator.evaluateExecutionConditions(conditions, event));
        }

        @Test
        void evaluateExecution_nullConditions_returnsTrue() {
            JsonObject event = new JsonObject();
            event.addProperty("description", "anything");

            assertTrue(evaluator.evaluateExecutionConditions((String) null, event));
        }

        @Test
        void evaluateExecution_emptyConditions_returnsTrue() {
            JsonObject event = new JsonObject();
            event.addProperty("description", "anything");

            assertTrue(evaluator.evaluateExecutionConditions("[]", event));
        }

        @Test
        void evaluateExecution_multipleConditions_allMustPass() {
            String conditions = "["
                    + "{\"field\":\"description\",\"operator\":\"contains\",\"value\":\"urgent\"},"
                    + "{\"field\":\"status\",\"operator\":\"equals\",\"value\":\"OPEN\"}"
                    + "]";
            JsonObject event = new JsonObject();
            event.addProperty("description", "urgent fix needed");
            event.addProperty("status", "OPEN");

            assertTrue(evaluator.evaluateExecutionConditions(conditions, event));

            // Flip the status so second condition fails
            event.addProperty("status", "CLOSED");
            assertFalse(evaluator.evaluateExecutionConditions(conditions, event));
        }

        @Test
        void evaluateExecution_invalidJson_returnsFalse() {
            JsonObject event = new JsonObject();
            event.addProperty("description", "test");

            assertFalse(evaluator.evaluateExecutionConditions("{not valid json!!", event));
        }

        @Test
        void executionCondition_unknownOperator_returnsFalse() {
            String conditions = "[{\"field\":\"status\",\"operator\":\"foobar\",\"value\":\"OPEN\"}]";
            JsonObject event = new JsonObject();
            event.addProperty("status", "OPEN");

            assertFalse(evaluator.evaluateExecutionConditions(conditions, event));
        }

        @Test
        void executionCondition_gt_nullField_returnsFalse() {
            String conditions = "[{\"field\":\"amount\",\"operator\":\"gt\",\"value\":\"100\"}]";
            JsonObject event = new JsonObject();
            // field "amount" is absent — resolveFieldAsString returns null

            assertFalse(evaluator.evaluateExecutionConditions(conditions, event));
        }

        @Test
        void executionCondition_lt_nullField_returnsFalse() {
            String conditions = "[{\"field\":\"amount\",\"operator\":\"lt\",\"value\":\"100\"}]";
            JsonObject event = new JsonObject();
            // field "amount" is absent

            assertFalse(evaluator.evaluateExecutionConditions(conditions, event));
        }

        @Test
        void executionCondition_gt_nonNumeric_returnsFalse() {
            String conditions = "[{\"field\":\"amount\",\"operator\":\"gt\",\"value\":\"5\"}]";
            JsonObject event = new JsonObject();
            event.addProperty("amount", "abc");

            assertFalse(evaluator.evaluateExecutionConditions(conditions, event));
        }

        @Test
        void executionCondition_lt_nonNumeric_returnsFalse() {
            String conditions = "[{\"field\":\"amount\",\"operator\":\"lt\",\"value\":\"5\"}]";
            JsonObject event = new JsonObject();
            event.addProperty("amount", "abc");

            assertFalse(evaluator.evaluateExecutionConditions(conditions, event));
        }

        @Test
        void executionCondition_malformedElement_returnsFalse() {
            // JSON array containing a primitive string instead of a JSON object
            String conditions = "[\"not-an-object\"]";
            JsonObject event = new JsonObject();
            event.addProperty("status", "OPEN");

            assertFalse(evaluator.evaluateExecutionConditions(conditions, event));
        }
    }

    // ---------------------------------------------------------------
    // Success Conditions  (evaluateSuccessConditions)
    // ---------------------------------------------------------------
    @Nested
    @DisplayName("Success conditions")
    class SuccessConditions {

        @Test
        void evaluateSuccess_statusRange_withinRange_returnsTrue() {
            String conditions = "[{\"operator\":\"status_range\",\"value\":\"200-299\"}]";
            TestResult result = buildResult(200, "{\"ok\":true}", true);

            assertTrue(evaluator.evaluateSuccessConditions(conditions, result));
        }

        @Test
        void evaluateSuccess_statusRange_outsideRange_returnsFalse() {
            String conditions = "[{\"operator\":\"status_range\",\"value\":\"200-299\"}]";
            TestResult result = buildResult(500, "Internal Server Error", false);

            assertFalse(evaluator.evaluateSuccessConditions(conditions, result));
        }

        @Test
        void evaluateSuccess_bodyContains_match() {
            String conditions = "[{\"operator\":\"body_contains\",\"value\":\"success\"}]";
            TestResult result = buildResult(200, "{\"status\":\"success\"}", true);

            assertTrue(evaluator.evaluateSuccessConditions(conditions, result));
        }

        @Test
        void evaluateSuccess_bodyContains_noMatch() {
            String conditions = "[{\"operator\":\"body_contains\",\"value\":\"success\"}]";
            TestResult result = buildResult(200, "{\"status\":\"error\"}", true);

            assertFalse(evaluator.evaluateSuccessConditions(conditions, result));
        }

        @Test
        void evaluateSuccess_bodyNotContains_works() {
            String conditions = "[{\"operator\":\"body_not_contains\",\"value\":\"error\"}]";
            TestResult result = buildResult(200, "{\"status\":\"ok\"}", true);

            assertTrue(evaluator.evaluateSuccessConditions(conditions, result));
        }

        @Test
        void evaluateSuccess_noConditions_defaultsTo2xxCheck() {
            TestResult successResult = buildResult(200, "ok", true);
            TestResult failResult = buildResult(500, "fail", false);

            assertTrue(evaluator.evaluateSuccessConditions((String) null, successResult));
            assertFalse(evaluator.evaluateSuccessConditions((String) null, failResult));
        }

        @Test
        void evaluateSuccess_multipleConditions_allMustPass() {
            String conditions = "["
                    + "{\"operator\":\"status_range\",\"value\":\"200-299\"},"
                    + "{\"operator\":\"body_contains\",\"value\":\"created\"}"
                    + "]";
            TestResult result = buildResult(201, "{\"id\":\"abc\",\"status\":\"created\"}", true);

            assertTrue(evaluator.evaluateSuccessConditions(conditions, result));

            // Body does not contain "created"
            TestResult noMatch = buildResult(201, "{\"id\":\"abc\"}", true);
            assertFalse(evaluator.evaluateSuccessConditions(conditions, noMatch));
        }

        @Test
        void successCondition_statusRange_nullStatus_returnsFalse() {
            String conditions = "[{\"operator\":\"status_range\",\"value\":\"200-299\"}]";
            TestResult result = new TestResult();
            result.setResponseStatus(null);
            result.setResponseBody("ok");
            result.setSuccess(true);

            assertFalse(evaluator.evaluateSuccessConditions(conditions, result));
        }

        @Test
        void successCondition_statusRange_malformedRange_returnsFalse() {
            String conditions = "[{\"operator\":\"status_range\",\"value\":\"abc-xyz\"}]";
            TestResult result = buildResult(200, "ok", true);

            assertFalse(evaluator.evaluateSuccessConditions(conditions, result));
        }

        @Test
        void successCondition_unknownOperator_returnsFalse() {
            String conditions = "[{\"operator\":\"unknown_op\",\"value\":\"anything\"}]";
            TestResult result = buildResult(200, "ok", true);

            assertFalse(evaluator.evaluateSuccessConditions(conditions, result));
        }
    }

    // ---------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------

    private TestResult buildResult(int status, String body, boolean success) {
        TestResult result = new TestResult();
        result.setResponseStatus(status);
        result.setResponseBody(body);
        result.setSuccess(success);
        return result;
    }
}
