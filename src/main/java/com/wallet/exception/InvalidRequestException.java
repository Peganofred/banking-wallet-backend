package com.wallet.exception;

import org.springframework.http.HttpStatus;

/**
 * Thrown when an API request is invalid (bad parameters, missing input, wrong amount).
 * -> HTTP 400 BAD REQUEST
 */
public class InvalidRequestException extends ApiException {
    public InvalidRequestException(String message) {
        super(HttpStatus.BAD_REQUEST, message);
    }
}
