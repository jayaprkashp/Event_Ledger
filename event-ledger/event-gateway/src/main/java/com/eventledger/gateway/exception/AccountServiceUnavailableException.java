package com.eventledger.gateway.exception;

/** Thrown when the Account Service is unreachable, times out, or the circuit breaker is open. */
public class AccountServiceUnavailableException extends RuntimeException {
    public AccountServiceUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
