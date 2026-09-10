package com.wallet.exception;

import org.springframework.http.HttpStatus;

/**
 * Thrown when the current user does not have permission to perform an action.
 * -> HTTP 403 FORBIDDEN
 */
public class AccessDeniedException extends ApiException {
    public AccessDeniedException(String message) {
        super(HttpStatus.FORBIDDEN, message);
    }
}
