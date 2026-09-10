package com.wallet.exception;

import org.springframework.http.HttpStatus;

/**
 * Base class for all our custom business exceptions.
 * Each exception provides its own HTTP status.
 */
public abstract class ApiException extends RuntimeException {
    private final HttpStatus status;

    protected ApiException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
