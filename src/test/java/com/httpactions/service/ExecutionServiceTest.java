package com.httpactions.service;

import com.google.gson.JsonObject;
import com.httpactions.model.dto.ExecutionCondition;
import com.httpactions.model.dto.SuccessCondition;
import com.httpactions.model.dto.TestResult;
import com.httpactions.model.entity.Action;
import com.httpactions.model.enums.EventType;
import com.httpactions.model.enums.HttpMethod;
import com.httpactions.repository.ActionRepository;
import com.httpactions.repository.ExecutionLogRepository;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import org.springframework.web.client.RestClient;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import org.junit.jupiter.api.BeforeEach;

@ExtendWith(MockitoExtension.class)
class ExecutionServiceTest {

    @Mock
    private ActionService actionService;

    @Mock
    private ActionRepository actionRepository;

    @Mock
    private VariableInterpolator templateEngine;

    @Mock
    private HttpActionExecutor httpActionExecutor;

    @Mock
    private TokenService tokenService;

    @Mock
    private ExecutionLogRepository executionLogRepository;

    @Mock
    private InstallationService installationService;

    @Mock
    private ConditionEvaluator conditionEvaluator;

    @Spy
    private SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();

    @Mock
    private java.util.concurrent.Executor taskExecutor;

    @Spy
    private Clock clock = Clock.fixed(Instant.parse("2026-03-30T12:00:00Z"), ZoneOffset.UTC);

    @Mock
    private Sleeper sleeper;

    @Mock
    private java.util.function.LongSupplier nanoTimeSupplier;

    @InjectMocks
    private ExecutionService executionService;

    @BeforeEach
    void setUp() throws Exception {
        // Make the mock executor run tasks immediately on the calling thread
        lenient().doAnswer(invocation -> {
            ((Runnable) invocation.getArgument(0)).run();
            return null;
        }).when(taskExecutor).execute(any(Runnable.class));
        lenient().doNothing().when(sleeper).sleep(anyLong());
        lenient().when(nanoTimeSupplier.getAsLong()).thenReturn(0L);
    }

    // ---------------------------------------------------------------
    // processWebhookAsync
    // ---------------------------------------------------------------

    @Test
    void processWebhookAsync_noActions_logsAndReturns() {
        when(actionRepository.findByWorkspaceIdAndEventTypeAndEnabledTrueOrderByChainOrderAscIdAsc(
                "ws-1", EventType.NEW_TIME_ENTRY))
                .thenReturn(Collections.emptyList());

        executionService.processWebhookAsync("ws-1", EventType.NEW_TIME_ENTRY, "{}");

        verify(actionRepository).findByWorkspaceIdAndEventTypeAndEnabledTrueOrderByChainOrderAscIdAsc(
                "ws-1", EventType.NEW_TIME_ENTRY);
        verifyNoInteractions(httpActionExecutor);
    }

    @Test
    void processWebhookAsync_independentActions_allExecuted() throws Exception {
        UUID id1 = UUID.randomUUID();
        UUID id2 = UUID.randomUUID();
        Action action1 = createTestAction(id1, EventType.NEW_TIME_ENTRY, null);
        Action action2 = createTestAction(id2, EventType.NEW_TIME_ENTRY, null);

        when(actionRepository.findByWorkspaceIdAndEventTypeAndEnabledTrueOrderByChainOrderAscIdAsc(
                "ws-1", EventType.NEW_TIME_ENTRY))
                .thenReturn(List.of(action1, action2));

        when(conditionEvaluator.evaluateExecutionConditions(org.mockito.ArgumentMatchers.<List<ExecutionCondition>>isNull(),
                any(JsonObject.class)))
                .thenReturn(true);

        when(templateEngine.interpolate(anyString(), any(JsonObject.class), anyMap(), eq(true), isNull()))
                .thenReturn("https://example.com/hook");
        when(templateEngine.interpolate(isNull(), any(JsonObject.class), anyMap(), eq(false), isNull()))
                .thenReturn(null);

        HttpActionExecutor.PreparedRequest preparedRequest = preparedRequest();
        doReturn(preparedRequest).when(httpActionExecutor).prepare(anyString());
        TestResult okResult = buildSuccessResult();
        when(httpActionExecutor.execute(any(HttpActionExecutor.PreparedRequest.class), eq("POST"), anyString(), any(), any(), isNull()))
                .thenReturn(okResult);

        when(conditionEvaluator.evaluateSuccessConditions(org.mockito.ArgumentMatchers.<List<SuccessCondition>>isNull(),
                any(TestResult.class)))
                .thenReturn(true);

        executionService.processWebhookAsync("ws-1", EventType.NEW_TIME_ENTRY,
                "{\"description\":\"test\"}");

        verify(httpActionExecutor, times(2))
                .execute(any(HttpActionExecutor.PreparedRequest.class), eq("POST"), anyString(), any(), any(), isNull());
    }

    @Test
    void processWebhookAsync_chainedActions_executedInOrder() throws Exception {
        UUID id1 = UUID.randomUUID();
        UUID id2 = UUID.randomUUID();
        Action action1 = createTestAction(id1, EventType.NEW_TIME_ENTRY, 1);
        Action action2 = createTestAction(id2, EventType.NEW_TIME_ENTRY, 2);

        when(actionRepository.findByWorkspaceIdAndEventTypeAndEnabledTrueOrderByChainOrderAscIdAsc(
                "ws-1", EventType.NEW_TIME_ENTRY))
                .thenReturn(List.of(action1, action2));

        when(conditionEvaluator.evaluateExecutionConditions(org.mockito.ArgumentMatchers.<List<ExecutionCondition>>isNull(),
                any(JsonObject.class)))
                .thenReturn(true);

        // First call: prevVars is null (first in chain). Second call: prevVars is non-null (has prev result).
        when(templateEngine.interpolate(anyString(), any(JsonObject.class), anyMap(), eq(true), any()))
                .thenReturn("https://example.com/hook");
        when(templateEngine.interpolate(isNull(), any(JsonObject.class), anyMap(), eq(false), any()))
                .thenReturn(null);

        HttpActionExecutor.PreparedRequest preparedRequest = preparedRequest();
        doReturn(preparedRequest).when(httpActionExecutor).prepare(anyString());
        TestResult okResult = buildSuccessResult();
        when(httpActionExecutor.execute(any(HttpActionExecutor.PreparedRequest.class), eq("POST"), anyString(), any(), any(), isNull()))
                .thenReturn(okResult);

        when(conditionEvaluator.evaluateSuccessConditions(org.mockito.ArgumentMatchers.<List<SuccessCondition>>isNull(),
                any(TestResult.class)))
                .thenReturn(true);

        executionService.processWebhookAsync("ws-1", EventType.NEW_TIME_ENTRY,
                "{\"description\":\"test\"}");

        // Both chained actions should be executed
        verify(httpActionExecutor, times(2))
                .execute(any(HttpActionExecutor.PreparedRequest.class), eq("POST"), anyString(), any(), any(), isNull());

        // The second call to templateEngine.interpolate (for URL) should receive non-null prevVars
        // First chained action gets null prevVars, second gets a map with prev.status, prev.body, etc.
        verify(templateEngine, times(1)).interpolate(anyString(), any(JsonObject.class), anyMap(), eq(true),
                isNull());
        verify(templateEngine, times(1)).interpolate(anyString(), any(JsonObject.class), anyMap(), eq(true),
                argThat(map -> map != null && map.containsKey("prev.status")));
    }

    @Test
    void processWebhookAsync_conditionsNotMet_actionSkipped() {
        UUID id1 = UUID.randomUUID();
        Action action1 = createTestAction(id1, EventType.NEW_TIME_ENTRY, null);
        action1.setExecutionConditions(List.of(executionCondition("description", "equals", "important")));

        when(actionRepository.findByWorkspaceIdAndEventTypeAndEnabledTrueOrderByChainOrderAscIdAsc(
                "ws-1", EventType.NEW_TIME_ENTRY))
                .thenReturn(List.of(action1));

        when(conditionEvaluator.evaluateExecutionConditions(eq(action1.getExecutionConditions()), any(JsonObject.class)))
                .thenReturn(false);

        executionService.processWebhookAsync("ws-1", EventType.NEW_TIME_ENTRY,
                "{\"description\":\"not important\"}");

        verifyNoInteractions(httpActionExecutor);
    }

    // ---------------------------------------------------------------
    // testAction
    // ---------------------------------------------------------------

    @Test
    void testAction_interpolatesAndExecutes() throws Exception {
        UUID id = UUID.randomUUID();
        Action action = createTestAction(id, EventType.NEW_TIME_ENTRY, null);
        action.setBodyTemplate("{\"text\":\"{{description}}\"}");
        String samplePayload = "{\"description\":\"hello\"}";

        when(templateEngine.interpolate(eq(action.getUrlTemplate()), any(JsonObject.class), anyMap(), eq(true)))
                .thenReturn("https://example.com/hook");
        when(templateEngine.interpolate(eq(action.getBodyTemplate()), any(JsonObject.class), anyMap(), eq(false)))
                .thenReturn("{\"text\":\"hello\"}");

        TestResult okResult = buildSuccessResult();
        when(httpActionExecutor.execute(eq("POST"), eq("https://example.com/hook"), isNull(),
                eq("{\"text\":\"hello\"}"), isNull()))
                .thenReturn(okResult);

        when(conditionEvaluator.evaluateSuccessConditions(org.mockito.ArgumentMatchers.<List<SuccessCondition>>isNull(),
                eq(okResult)))
                .thenReturn(true);

        TestResult result = executionService.testAction(action, samplePayload);

        assertNotNull(result);
        assertTrue(result.isSuccess());
        assertEquals(200, result.getResponseStatus());

        verify(templateEngine).interpolate(eq(action.getUrlTemplate()), any(JsonObject.class), anyMap(), eq(true));
        verify(templateEngine).interpolate(eq(action.getBodyTemplate()), any(JsonObject.class), anyMap(), eq(false));
        verify(httpActionExecutor).execute(eq("POST"), eq("https://example.com/hook"),
                isNull(), eq("{\"text\":\"hello\"}"), isNull());
        verify(executionLogRepository).save(any());
    }

    // ---------------------------------------------------------------
    // executeWithRetry (tested via processWebhookAsync)
    // ---------------------------------------------------------------

    @Test
    void executeWithRetry_successOnFirstAttempt_noRetry() throws Exception {
        UUID id = UUID.randomUUID();
        Action action = createTestAction(id, EventType.NEW_TIME_ENTRY, null);
        action.setRetryCount(3);

        when(actionRepository.findByWorkspaceIdAndEventTypeAndEnabledTrueOrderByChainOrderAscIdAsc(
                "ws-1", EventType.NEW_TIME_ENTRY))
                .thenReturn(List.of(action));

        when(conditionEvaluator.evaluateExecutionConditions(org.mockito.ArgumentMatchers.<List<ExecutionCondition>>isNull(),
                any(JsonObject.class)))
                .thenReturn(true);

        when(templateEngine.interpolate(anyString(), any(JsonObject.class), anyMap(), eq(true), isNull()))
                .thenReturn("https://example.com/hook");
        when(templateEngine.interpolate(isNull(), any(JsonObject.class), anyMap(), eq(false), isNull()))
                .thenReturn(null);

        HttpActionExecutor.PreparedRequest preparedRequest = preparedRequest();
        doReturn(preparedRequest).when(httpActionExecutor).prepare(anyString());
        TestResult okResult = buildSuccessResult();
        when(httpActionExecutor.execute(any(HttpActionExecutor.PreparedRequest.class), eq("POST"), anyString(), any(), any(), isNull()))
                .thenReturn(okResult);

        when(conditionEvaluator.evaluateSuccessConditions(org.mockito.ArgumentMatchers.<List<SuccessCondition>>isNull(),
                any(TestResult.class)))
                .thenReturn(true);

        executionService.processWebhookAsync("ws-1", EventType.NEW_TIME_ENTRY,
                "{\"description\":\"test\"}");

        // Success on first attempt — httpActionExecutor should be called exactly once
        verify(httpActionExecutor, times(1))
                .execute(any(HttpActionExecutor.PreparedRequest.class), eq("POST"), anyString(), any(), any(), isNull());
    }

    @Test
    void executeWithRetry_4xxError_noRetry() throws Exception {
        UUID id = UUID.randomUUID();
        Action action = createTestAction(id, EventType.NEW_TIME_ENTRY, null);
        action.setRetryCount(3);

        when(actionRepository.findByWorkspaceIdAndEventTypeAndEnabledTrueOrderByChainOrderAscIdAsc(
                "ws-1", EventType.NEW_TIME_ENTRY))
                .thenReturn(List.of(action));

        when(conditionEvaluator.evaluateExecutionConditions(org.mockito.ArgumentMatchers.<List<ExecutionCondition>>isNull(),
                any(JsonObject.class)))
                .thenReturn(true);

        when(templateEngine.interpolate(anyString(), any(JsonObject.class), anyMap(), eq(true), isNull()))
                .thenReturn("https://example.com/hook");
        when(templateEngine.interpolate(isNull(), any(JsonObject.class), anyMap(), eq(false), isNull()))
                .thenReturn(null);

        HttpActionExecutor.PreparedRequest preparedRequest = preparedRequest();
        doReturn(preparedRequest).when(httpActionExecutor).prepare(anyString());
        TestResult notFoundResult = new TestResult();
        notFoundResult.setResponseStatus(404);
        notFoundResult.setResponseBody("{\"error\":\"not found\"}");
        notFoundResult.setSuccess(false);
        notFoundResult.setResponseTimeMs(15);

        when(httpActionExecutor.execute(any(HttpActionExecutor.PreparedRequest.class), eq("POST"), anyString(), any(), any(), isNull()))
                .thenReturn(notFoundResult);

        when(conditionEvaluator.evaluateSuccessConditions(org.mockito.ArgumentMatchers.<List<SuccessCondition>>isNull(),
                any(TestResult.class)))
                .thenReturn(false);

        executionService.processWebhookAsync("ws-1", EventType.NEW_TIME_ENTRY,
                "{\"description\":\"test\"}");

        // 4xx error should NOT trigger retries — called exactly once despite retryCount=3
        verify(httpActionExecutor, times(1))
                .execute(any(HttpActionExecutor.PreparedRequest.class), eq("POST"), anyString(), any(), any(), isNull());
    }

    @Test
    void executeWithRetry_5xxThenSuccess_retries() throws Exception {
        UUID id = UUID.randomUUID();
        Action action = createTestAction(id, EventType.NEW_TIME_ENTRY, null);
        action.setRetryCount(1);

        when(actionRepository.findByWorkspaceIdAndEventTypeAndEnabledTrueOrderByChainOrderAscIdAsc(
                "ws-1", EventType.NEW_TIME_ENTRY))
                .thenReturn(List.of(action));

        when(conditionEvaluator.evaluateExecutionConditions(org.mockito.ArgumentMatchers.<List<ExecutionCondition>>isNull(),
                any(JsonObject.class)))
                .thenReturn(true);

        when(templateEngine.interpolate(anyString(), any(JsonObject.class), anyMap(), eq(true), isNull()))
                .thenReturn("https://example.com/hook");
        when(templateEngine.interpolate(isNull(), any(JsonObject.class), anyMap(), eq(false), isNull()))
                .thenReturn(null);

        HttpActionExecutor.PreparedRequest preparedRequest = preparedRequest();
        doReturn(preparedRequest).when(httpActionExecutor).prepare(anyString());
        TestResult serverError = new TestResult();
        serverError.setResponseStatus(500);
        serverError.setResponseBody("{\"error\":\"internal server error\"}");
        serverError.setSuccess(false);
        serverError.setResponseTimeMs(100);

        TestResult okResult = buildSuccessResult();

        // First call returns 500, second call returns 200
        when(httpActionExecutor.execute(any(HttpActionExecutor.PreparedRequest.class), eq("POST"), anyString(), any(), any(), isNull()))
                .thenReturn(serverError)
                .thenReturn(okResult);

        when(conditionEvaluator.evaluateSuccessConditions(org.mockito.ArgumentMatchers.<List<SuccessCondition>>isNull(),
                any(TestResult.class)))
                .thenReturn(true);

        executionService.processWebhookAsync("ws-1", EventType.NEW_TIME_ENTRY,
                "{\"description\":\"test\"}");

        // 5xx triggers retry — should be called twice (initial + 1 retry)
        verify(httpActionExecutor, times(2))
                .execute(any(HttpActionExecutor.PreparedRequest.class), eq("POST"), anyString(), any(), any(), isNull());
    }

    // ---------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------

    private Action createTestAction(UUID id, EventType eventType, Integer chainOrder) {
        Action a = new Action();
        a.setId(id);
        a.setWorkspaceId("ws-1");
        a.setEventType(eventType);
        a.setHttpMethod(HttpMethod.POST);
        a.setUrlTemplate("https://example.com/hook");
        a.setRetryCount(0);
        a.setEnabled(true);
        a.setChainOrder(chainOrder);
        return a;
    }

    private ExecutionCondition executionCondition(String field, String operator, String value) {
        ExecutionCondition condition = new ExecutionCondition();
        condition.setField(field);
        condition.setOperator(operator);
        condition.setValue(value);
        return condition;
    }

    private TestResult buildSuccessResult() {
        TestResult result = new TestResult();
        result.setResponseStatus(200);
        result.setResponseBody("{\"ok\":true}");
        result.setSuccess(true);
        result.setResponseTimeMs(42);
        return result;
    }

    private HttpActionExecutor.PreparedRequest preparedRequest() {
        return new HttpActionExecutor.PreparedRequest(
                new ValidatedUrl(java.net.URI.create("https://example.com/hook"), "example.com", mock(java.net.InetAddress.class)),
                mock(CloseableHttpClient.class),
                mock(RestClient.class)
        );
    }
}
