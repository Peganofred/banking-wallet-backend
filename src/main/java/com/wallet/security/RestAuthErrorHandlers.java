package com.wallet.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wallet.exception.ApiError;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Sends JSON {ApiError} instead of Spring's default empty 401/403.
 *
 * 401 -> token missing/invalid/expired (authentication problem)
 * 403 -> authenticated but not allowed (authorization problem)
 */
@Component
public class RestAuthErrorHandlers implements AuthenticationEntryPoint, AccessDeniedHandler {

    private final ObjectMapper objectMapper;

    public RestAuthErrorHandlers(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void commence(HttpServletRequest request,
                         HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        send(response, 401, "Unauthorized", "Authentication required - provide a valid JWT token", request.getRequestURI());
    }

    @Override
    public void handle(HttpServletRequest request,
                       HttpServletResponse response,
                       AccessDeniedException accessDeniedException) throws IOException {
        send(response, 403, "Forbidden", "You do not have permission to perform this action", request.getRequestURI());
    }

    private void send(HttpServletResponse response,
                      int status,
                      String error,
                      String message,
                      String path) throws IOException {
        ApiError apiError = ApiError.of(status, error, message, path);
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), apiError);
    }
}