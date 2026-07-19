package com.eventledger.account.exception;

/** Domain-level rejection beyond bean validation, e.g. a currency mismatch on an existing account. */
public class InvalidTransactionException extends RuntimeException {
    public InvalidTransactionException(String message) {
        super(message);
    }
}
