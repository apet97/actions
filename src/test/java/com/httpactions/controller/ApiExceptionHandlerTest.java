package com.httpactions.controller;

import com.httpactions.model.dto.ApiErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;

import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ApiExceptionHandlerTest {

    private ApiExceptionHandler handler;
    private HttpServletRequest request;

    @BeforeEach
    void setUp() {
        handler = new ApiExceptionHandler();
        request = mock(HttpServletRequest.class);
        when(request.getRequestURI()).thenReturn("/api/actions");
    }

    // ── MissingRequestHeaderException → 401 ──

    @Test
    @DisplayName("MissingRequestHeaderException returns 401 with header name in message")
    void handleMissingHeader_returns401WithHeaderName() throws NoSuchMethodException {
        MethodParameter param = new MethodParameter(
                ApiExceptionHandlerTest.class.getDeclaredMethod("dummyMethod", String.class), 0);
        MissingRequestHeaderException ex = new MissingRequestHeaderException("X-Addon-Token", param);

        ResponseEntity<ApiErrorResponse> response = handler.handleMissingHeader(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        ApiErrorResponse body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.status()).isEqualTo(401);
        assertThat(body.error()).isEqualTo("Unauthorized");
        assertThat(body.message()).isEqualTo("Missing required header: X-Addon-Token");
        assertThat(body.path()).isEqualTo("/api/actions");
        assertThat(body.fieldErrors()).isEmpty();
        assertThat(body.timestamp()).isNotNull();
    }

    // ── MethodArgumentNotValidException → 400 with field errors ──

    @Test
    @DisplayName("MethodArgumentNotValidException returns 400 with field errors map")
    void handleValidation_returns400WithFieldErrors() throws NoSuchMethodException {
        BindingResult bindingResult = mock(BindingResult.class);
        when(bindingResult.getFieldErrors()).thenReturn(List.of(
                new FieldError("actionRequest", "name", "must not be blank"),
                new FieldError("actionRequest", "urlTemplate", "must not be blank"),
                new FieldError("actionRequest", "name", "size must be between 1 and 100")
        ));

        MethodParameter param = new MethodParameter(
                ApiExceptionHandlerTest.class.getDeclaredMethod("dummyMethod", String.class), 0);
        MethodArgumentNotValidException ex = new MethodArgumentNotValidException(param, bindingResult);

        ResponseEntity<ApiErrorResponse> response = handler.handleValidation(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        ApiErrorResponse body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.status()).isEqualTo(400);
        assertThat(body.error()).isEqualTo("Bad Request");
        assertThat(body.message()).isEqualTo("Validation failed");
        assertThat(body.path()).isEqualTo("/api/actions");
        assertThat(body.fieldErrors()).containsEntry("name", "must not be blank");
        assertThat(body.fieldErrors()).containsEntry("urlTemplate", "must not be blank");
        // putIfAbsent keeps first error per field — second "name" error is dropped
        assertThat(body.fieldErrors()).hasSize(2);
    }

    @Test
    @DisplayName("MethodArgumentNotValidException with no field errors returns empty fieldErrors map")
    void handleValidation_noFieldErrors_returnsEmptyMap() throws NoSuchMethodException {
        BindingResult bindingResult = mock(BindingResult.class);
        when(bindingResult.getFieldErrors()).thenReturn(List.of());

        MethodParameter param = new MethodParameter(
                ApiExceptionHandlerTest.class.getDeclaredMethod("dummyMethod", String.class), 0);
        MethodArgumentNotValidException ex = new MethodArgumentNotValidException(param, bindingResult);

        ResponseEntity<ApiErrorResponse> response = handler.handleValidation(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().fieldErrors()).isEmpty();
    }

    // ── ConstraintViolationException → 400 ──

    @Test
    @DisplayName("ConstraintViolationException returns 400 with violation message")
    void handleConstraintViolation_returns400() {
        @SuppressWarnings("unchecked")
        ConstraintViolation<Object> violation = mock(ConstraintViolation.class);
        when(violation.getMessage()).thenReturn("must be positive");
        ConstraintViolationException ex = new ConstraintViolationException("retryCount: must be positive", Set.of(violation));

        ResponseEntity<ApiErrorResponse> response = handler.handleConstraintViolation(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        ApiErrorResponse body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.status()).isEqualTo(400);
        assertThat(body.error()).isEqualTo("Bad Request");
        assertThat(body.message()).contains("must be positive");
        assertThat(body.path()).isEqualTo("/api/actions");
        assertThat(body.fieldErrors()).isEmpty();
    }

    // ── IllegalArgumentException → 400 ──

    @Test
    @DisplayName("IllegalArgumentException returns 400 with exception message")
    void handleIllegalArgument_returns400() {
        IllegalArgumentException ex = new IllegalArgumentException("Invalid event type: UNKNOWN");

        ResponseEntity<ApiErrorResponse> response = handler.handleBadRequest(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        ApiErrorResponse body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.status()).isEqualTo(400);
        assertThat(body.error()).isEqualTo("Bad Request");
        assertThat(body.message()).isEqualTo("Invalid event type: UNKNOWN");
        assertThat(body.path()).isEqualTo("/api/actions");
        assertThat(body.fieldErrors()).isEmpty();
    }

    // ── IllegalStateException → 500 ──

    @Test
    @DisplayName("IllegalStateException returns 500 without leaking the exception message")
    void handleIllegalState_returns500() {
        IllegalStateException ex = new IllegalStateException("Action is disabled");

        ResponseEntity<ApiErrorResponse> response = handler.handleIllegalState(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        ApiErrorResponse body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.status()).isEqualTo(500);
        assertThat(body.error()).isEqualTo("Internal Server Error");
        assertThat(body.message()).isEqualTo("Unexpected server error");
        assertThat(body.path()).isEqualTo("/api/actions");
        assertThat(body.fieldErrors()).isEmpty();
    }

    // ── NoSuchElementException → 404 ──

    @Test
    @DisplayName("NoSuchElementException returns 404 with exception message")
    void handleNotFound_returns404() {
        NoSuchElementException ex = new NoSuchElementException("Action not found: abc-123");

        ResponseEntity<ApiErrorResponse> response = handler.handleNotFound(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        ApiErrorResponse body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.status()).isEqualTo(404);
        assertThat(body.error()).isEqualTo("Not Found");
        assertThat(body.message()).isEqualTo("Action not found: abc-123");
        assertThat(body.path()).isEqualTo("/api/actions");
        assertThat(body.fieldErrors()).isEmpty();
    }

    // ── Generic Exception → 500 ──

    @Test
    @DisplayName("Generic Exception returns 500 with 'Unexpected server error' message")
    void handleGeneric_returns500WithGenericMessage() {
        Exception ex = new RuntimeException("Database connection lost");

        ResponseEntity<ApiErrorResponse> response = handler.handleGeneric(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        ApiErrorResponse body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.status()).isEqualTo(500);
        assertThat(body.error()).isEqualTo("Internal Server Error");
        assertThat(body.message()).isEqualTo("Unexpected server error");
        assertThat(body.path()).isEqualTo("/api/actions");
        assertThat(body.fieldErrors()).isEmpty();
    }

    @Test
    @DisplayName("Generic Exception does not leak original exception message to response")
    void handleGeneric_doesNotLeakExceptionMessage() {
        Exception ex = new NullPointerException("sensitive.internal.field was null");

        ResponseEntity<ApiErrorResponse> response = handler.handleGeneric(ex, request);

        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().message()).doesNotContain("sensitive");
        assertThat(response.getBody().message()).isEqualTo("Unexpected server error");
    }

    // ── Response structure ──

    @Test
    @DisplayName("All responses include non-null timestamp and correct request path")
    void allResponses_includeTimestampAndPath() {
        when(request.getRequestURI()).thenReturn("/api/actions/550e8400-e29b-41d4-a716-446655440000/test");

        IllegalArgumentException ex = new IllegalArgumentException("bad input");
        ResponseEntity<ApiErrorResponse> response = handler.handleBadRequest(ex, request);

        ApiErrorResponse body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.timestamp()).isNotNull();
        assertThat(body.path()).isEqualTo("/api/actions/550e8400-e29b-41d4-a716-446655440000/test");
    }

    // ── Helper for MethodParameter construction ──

    @SuppressWarnings("unused")
    void dummyMethod(String param) {
        // used to construct MethodParameter for MissingRequestHeaderException
    }
}
