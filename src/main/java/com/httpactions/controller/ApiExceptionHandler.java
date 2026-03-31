package com.httpactions.controller;

import com.httpactions.model.dto.ApiErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.NoSuchElementException;

@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<ApiErrorResponse> handleMissingHeader(
            MissingRequestHeaderException ex, HttpServletRequest request) {
        return build(HttpStatus.UNAUTHORIZED, "Missing required header: " + ex.getHeaderName(), request, Map.of());
    }

    @ExceptionHandler(UnauthorizedAddonException.class)
    public ResponseEntity<ApiErrorResponse> handleUnauthorizedAddon(
            UnauthorizedAddonException ex, HttpServletRequest request) {
        return build(HttpStatus.UNAUTHORIZED, ex.getMessage(), request, Map.of());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleValidation(
            MethodArgumentNotValidException ex, HttpServletRequest request) {
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        for (FieldError error : ex.getBindingResult().getFieldErrors()) {
            fieldErrors.putIfAbsent(error.getField(), error.getDefaultMessage());
        }
        return build(HttpStatus.BAD_REQUEST, "Validation failed", request, fieldErrors);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiErrorResponse> handleConstraintViolation(
            ConstraintViolationException ex, HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST, ex.getMessage(), request, Map.of());
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiErrorResponse> handleUnreadableBody(
            HttpMessageNotReadableException ex, HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST, "Malformed request body", request, Map.of());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiErrorResponse> handleBadRequest(
            IllegalArgumentException ex, HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST, ex.getMessage(), request, Map.of());
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ApiErrorResponse> handleIllegalState(
            IllegalStateException ex, HttpServletRequest request) {
        log.error("Illegal state at {}: {}", request.getRequestURI(), ex.getMessage(), ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "Unexpected server error", request, Map.of());
    }

    @ExceptionHandler(NoSuchElementException.class)
    public ResponseEntity<ApiErrorResponse> handleNotFound(
            NoSuchElementException ex, HttpServletRequest request) {
        return build(HttpStatus.NOT_FOUND, ex.getMessage(), request, Map.of());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleGeneric(
            Exception ex, HttpServletRequest request) {
        log.error("Unhandled exception at {}: {}", request.getRequestURI(), ex.getMessage(), ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "Unexpected server error", request, Map.of());
    }

    private ResponseEntity<ApiErrorResponse> build(
            HttpStatus status, String message, HttpServletRequest request, Map<String, String> fieldErrors) {
        return ResponseEntity.status(status).body(new ApiErrorResponse(
                Instant.now(),
                status.value(),
                status.getReasonPhrase(),
                message,
                request.getRequestURI(),
                fieldErrors
        ));
    }
}
