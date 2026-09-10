package com.wallet.exception;

import org.springframework.http.HttpStatus;

/**
 * Thrown when a resource that must be unique already exists
 * (e.g. registering with an email that is already in use).
 * -> HTTP 409 CONFLICT
 */
public class DuplicateResourceException extends ApiException {
    public DuplicateResourceException(String message) {
        super(HttpStatus.CONFLICT, message);
    }
}
