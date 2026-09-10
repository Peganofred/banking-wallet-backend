package com.wallet.exception;

import org.springframework.http.HttpStatus;

/**
 * Thrown when a requested resource (user, wallet, transaction) does not exist.
 * -> HTTP 404 NOT FOUND
 */
public class ResourceNotFoundException extends ApiException {
    public ResourceNotFoundException(String message) {
        super(HttpStatus.NOT_FOUND, message);
    }
}
