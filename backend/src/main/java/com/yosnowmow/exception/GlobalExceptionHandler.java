package com.yosnowmow.exception;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Centralised exception handler for all REST controllers.
 *
 * All responses follow RFC 7807 Problem JSON format:
 *   { "type", "title", "status", "detail", "instance", "timestamp" }
 *
 * Stack traces are NEVER included in responses — logged server-side only.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    // ── 404 Not Found ──────────────────────────────────────────────────────

    @ExceptionHandler(JobNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleJobNotFound(
            JobNotFoundException ex, HttpServletRequest request) {

        return problem(HttpStatus.NOT_FOUND,
                "Job Not Found",
                ex.getMessage(),
                request.getRequestURI());
    }

    @ExceptionHandler(UserNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleUserNotFound(
            UserNotFoundException ex, HttpServletRequest request) {

        return problem(HttpStatus.NOT_FOUND,
                "User Not Found",
                ex.getMessage(),
                request.getRequestURI());
    }

    // ── 409 Conflict ───────────────────────────────────────────────────────

    @ExceptionHandler(InvalidTransitionException.class)
    public ResponseEntity<Map<String, Object>> handleInvalidTransition(
            InvalidTransitionException ex, HttpServletRequest request) {

        return problem(HttpStatus.CONFLICT,
                "Invalid State Transition",
                ex.getMessage(),
                request.getRequestURI());
    }

    // ── 402 Payment Required ───────────────────────────────────────────────

    @ExceptionHandler(PaymentException.class)
    public ResponseEntity<Map<String, Object>> handlePayment(
            PaymentException ex, HttpServletRequest request) {

        // Log cause for debugging (e.g. Stripe API error details)
        log.error("Payment error at {}: {}", request.getRequestURI(), ex.getMessage(), ex);

        return problem(HttpStatus.PAYMENT_REQUIRED,
                "Payment Error",
                ex.getMessage(),
                request.getRequestURI());
    }

    // ── 400 Bad Request — validation errors ───────────────────────────────

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(
            MethodArgumentNotValidException ex, HttpServletRequest request) {

        // Collect all field-level validation errors into a readable list
        List<String> fieldErrors = ex.getBindingResult().getFieldErrors()
                .stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.toList());

        Map<String, Object> body = problemBody(HttpStatus.BAD_REQUEST,
                "Validation Failed",
                "One or more fields failed validation",
                request.getRequestURI());
        body.put("errors", fieldErrors);

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    // ── 403 Forbidden ──────────────────────────────────────────────────────

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Map<String, Object>> handleAccessDenied(
            AccessDeniedException ex, HttpServletRequest request) {

        return problem(HttpStatus.FORBIDDEN,
                "Access Denied",
                "You do not have permission to perform this action",
                request.getRequestURI());
    }

    // ── ResponseStatusException — preserve the intended HTTP status ──────────
    //
    // IMPORTANT: this handler must exist, otherwise ResponseStatusException
    // (thrown by service-layer code via `new ResponseStatusException(422, ...)`)
    // falls through to the Exception catch-all below and is incorrectly returned
    // as HTTP 500.  Spring's built-in resolver for ResponseStatusException only
    // runs when @RestControllerAdvice has no matching handler — but the catch-all
    // above prevents that fallback.

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, Object>> handleResponseStatus(
            ResponseStatusException ex, HttpServletRequest request) {

        HttpStatus status = HttpStatus.resolve(ex.getStatusCode().value());
        if (status == null) {
            status = HttpStatus.INTERNAL_SERVER_ERROR;
        }
        String detail = ex.getReason() != null ? ex.getReason() : "An error occurred";
        return problem(status, status.getReasonPhrase(), detail, request.getRequestURI());
    }

    // ── 500 Internal Server Error — catch-all ─────────────────────────────

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleUnexpected(
            Exception ex, HttpServletRequest request) {

        // Always log full stack trace for unexpected errors
        log.error("Unexpected error at {}", request.getRequestURI(), ex);

        // NEVER expose internal error details or stack traces to the client
        return problem(HttpStatus.INTERNAL_SERVER_ERROR,
                "Internal Server Error",
                "An unexpected error occurred. Please try again later.",
                request.getRequestURI());
    }

    // ── Helpers ────────────────────────────────────────────────────────────

    private ResponseEntity<Map<String, Object>> problem(
            HttpStatus status, String title, String detail, String instance) {

        return ResponseEntity.status(status).body(problemBody(status, title, detail, instance));
    }

    /**
     * Builds an RFC 7807 Problem JSON body.
     * Returns a LinkedHashMap so fields appear in a consistent order.
     */
    private Map<String, Object> problemBody(
            HttpStatus status, String title, String detail, String instance) {

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("type",      "https://yosnowmow.com/errors/" + title.toLowerCase().replace(' ', '-'));
        body.put("title",     title);
        body.put("status",    status.value());
        body.put("detail",    detail);
        body.put("instance",  instance);
        body.put("timestamp", Instant.now().toString());
        return body;
    }
}
