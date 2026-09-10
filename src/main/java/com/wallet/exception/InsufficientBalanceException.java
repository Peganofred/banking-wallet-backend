package com.wallet.exception;

import org.springframework.http.HttpStatus;

/**
 * Thrown when wallet balance is not enough for a withdrawal or transfer.
 * -> HTTP 400 BAD REQUEST
 */
public class InsufficientBalanceException extends ApiException {
    public InsufficientBalanceException(String message) {
        super(HttpStatus.BAD_REQUEST, message);
    }
}
