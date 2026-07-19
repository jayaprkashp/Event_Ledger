package com.eventledger.gateway.exception;

/** Thrown when the Account Service responds but explicitly rejects the transaction (non-retryable). */
public class DownstreamRejectionException extends RuntimeException {
    public DownstreamRejectionException(String message) {
        super(message);
    }
}
