package com.eventledger.gateway.exception;

/**
 * Subset of AccountServiceUnavailableException that IS worth retrying: timeouts,
 * connection failures, and 409 concurrent-update conflicts from the Account Service.
 * Kept as a distinct type so the Resilience4j retry policy can target exactly these
 * cases via its retry-exceptions list, without retrying against an already-open
 * circuit or a definitive rejection.
 */
public class RetryableDownstreamException extends AccountServiceUnavailableException {
    public RetryableDownstreamException(String message, Throwable cause) {
        super(message, cause);
    }
}
