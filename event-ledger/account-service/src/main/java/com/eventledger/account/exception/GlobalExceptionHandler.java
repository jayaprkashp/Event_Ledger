package com.eventledger.account.exception;

import com.eventledger.account.dto.response.ErrorResponse;
import com.eventledger.account.tracing.TraceContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .orElse("Validation failed");
        return ResponseEntity.badRequest()
                .body(ErrorResponse.of("VALIDATION_ERROR", message, TraceContext.current()));
    }

    @ExceptionHandler(AccountNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(AccountNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ErrorResponse.of("ACCOUNT_NOT_FOUND", ex.getMessage(), TraceContext.current()));
    }

    @ExceptionHandler(InvalidTransactionException.class)
    public ResponseEntity<ErrorResponse> handleInvalid(InvalidTransactionException ex) {
        return ResponseEntity.badRequest()
                .body(ErrorResponse.of("INVALID_TRANSACTION", ex.getMessage(), TraceContext.current()));
    }

    // Concurrent writers collided on the SAME account row despite the
    // pessimistic lock (e.g. a lock-timeout edge case) -- surfaced as 409 so
    // the Gateway knows a retry is meaningful, per Artifact 14's decision.
    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ResponseEntity<ErrorResponse> handleConcurrentUpdate(ObjectOptimisticLockingFailureException ex) {
        log.warn("Concurrent account update conflict traceId={}", TraceContext.current());
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ErrorResponse.of("CONCURRENT_UPDATE", "Account was updated concurrently, please retry", TraceContext.current()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception ex) {
        log.error("Unhandled exception traceId={}", TraceContext.current(), ex);
        return ResponseEntity.internalServerError()
                .body(ErrorResponse.of("INTERNAL_ERROR", "An unexpected error occurred", TraceContext.current()));
    }
}
