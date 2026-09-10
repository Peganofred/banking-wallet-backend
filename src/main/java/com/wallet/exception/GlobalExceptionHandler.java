package com.wallet.exception;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.context.request.ServletWebRequest;

import java.util.HashMap;
import java.util.Map;

/**
 * GLOBAL exception handler.
 *
 * This single class catches EVERY exception thrown by any controller
 * and converts it into a clean, uniform JSON response.
 *
 * Without this, Spring Boot would return its own default error page and
 * the client would get inconsistent responses. With this, every error
 * looks like:
 *
 * {
 *   "timestamp": "...",
 *   "status": 404,
 *   "error": "Not Found",
 *   "message": "...",
 *   "path": "/api/v1/...",
 *   "fieldErrors": null
 * }
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * Handler for ALL our custom business exceptions (ApiException subclasses).
     * Reads the status from the exception itself.
     */
    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ApiError> handleApiException(ApiException ex, WebRequest request) {
        HttpStatus status = ex.getStatus();
        String path = getPath(request);
        ApiError error = ApiError.of(status.value(), status.getReasonPhrase(), ex.getMessage(), path);
        return ResponseEntity.status(status).body(error);
    }

    /**
     * Handler for @Valid validation failures on request bodies.
     * Collects ALL field errors into the fieldErrors map.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException ex, WebRequest request) {
        HttpStatus status = HttpStatus.BAD_REQUEST;
        Map<String, String> fieldErrors = new HashMap<>();
        for (FieldError fe : ex.getBindingResult().getFieldErrors()) {
            fieldErrors.put(fe.getField(), fe.getDefaultMessage());
        }
        String path = getPath(request);
        ApiError error = ApiError.of(
                status.value(),
                status.getReasonPhrase(),
                "Validation failed for " + fieldErrors.size() + " field(s)",
                path,
                fieldErrors
        );
        return ResponseEntity.status(status).body(error);
    }

    /**
     * Handler for missing/invalid body, headers, path variables & params.
     * e.g. calling deposit WITHOUT the Idempotency-Key header.
     * -> 400 BAD REQUEST
     */
    @ExceptionHandler({
            org.springframework.web.bind.MissingRequestHeaderException.class,
            org.springframework.web.bind.MissingServletRequestParameterException.class,
            org.springframework.web.bind.MissingPathVariableException.class,
            org.springframework.http.converter.HttpMessageNotReadableException.class
    })
    public ResponseEntity<ApiError> handleMissingRequestPart(Exception ex, WebRequest request) {
        HttpStatus status = HttpStatus.BAD_REQUEST;
        String path = getPath(request);
        String message = "Missing or malformed request: " + ex.getMessage();
        ApiError error = ApiError.of(status.value(), status.getReasonPhrase(), message, path);
        return ResponseEntity.status(status).body(error);
    }

    /**
     * Handler for bad credentials (wrong email/password on login).
     * -> 401 UNAUTHORIZED
     */
    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ApiError> handleBadCredentials(BadCredentialsException ex, WebRequest request) {
        HttpStatus status = HttpStatus.UNAUTHORIZED;
        String path = getPath(request);
        ApiError error = ApiError.of(status.value(), status.getReasonPhrase(), "Invalid email or password", path);
        return ResponseEntity.status(status).body(error);
    }

    /**
     * Handler for Spring Security auth failures (missing/invalid token).
     * -> 401 UNAUTHORIZED
     */
    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiError> handleAuthentication(AuthenticationException ex, WebRequest request) {
        HttpStatus status = HttpStatus.UNAUTHORIZED;
        String path = getPath(request);
        ApiError error = ApiError.of(status.value(), status.getReasonPhrase(), "Authentication required", path);
        return ResponseEntity.status(status).body(error);
    }

    /**
     * Handler for Spring Security authorization failures (logged in but not allowed).
     * -> 403 FORBIDDEN
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiError> handleSecurityAccessDenied(AccessDeniedException ex, WebRequest request) {
        HttpStatus status = HttpStatus.FORBIDDEN;
        String path = getPath(request);
        ApiError error = ApiError.of(status.value(), status.getReasonPhrase(), "You do not have permission to perform this action", path);
        return ResponseEntity.status(status).body(error);
    }

    /**
     * Catch-all handler for any unhandled exception.
     * Prevents sensitive details from leaking to the client.
     * -> 500 INTERNAL SERVER ERROR
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleGeneric(Exception ex, WebRequest request) {
        log.error("Unhandled exception", ex);
        HttpStatus status = HttpStatus.INTERNAL_SERVER_ERROR;
        String path = getPath(request);
        ApiError error = ApiError.of(status.value(), status.getReasonPhrase(), "An unexpected error occurred", path);
        return ResponseEntity.status(status).body(error);
    }

    private String getPath(WebRequest request) {
        if (request instanceof ServletWebRequest servletWebRequest) {
            return servletWebRequest.getRequest().getRequestURI();
        }
        return request.getDescription(false);
    }
}
