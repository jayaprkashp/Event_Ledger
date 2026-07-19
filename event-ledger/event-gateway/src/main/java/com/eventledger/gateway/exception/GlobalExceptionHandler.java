package com.eventledger.gateway.exception;

import com.eventledger.gateway.dto.response.ErrorResponse;
import com.eventledger.gateway.tracing.TraceContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
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
        log.info("Validation error traceId={} message={}", TraceContext.current(), message);
        return ResponseEntity.badRequest()
                .body(ErrorResponse.of("VALIDATION_ERROR", message, TraceContext.current()));
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ErrorResponse> handleMissingParam(MissingServletRequestParameterException ex) {
        return ResponseEntity.badRequest()
                .body(ErrorResponse.of("VALIDATION_ERROR",
                        "Missing required parameter: " + ex.getParameterName(), TraceContext.current()));
    }

    @ExceptionHandler(EventNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(EventNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ErrorResponse.of("EVENT_NOT_FOUND", ex.getMessage(), TraceContext.current()));
    }

    @ExceptionHandler(AccountServiceUnavailableException.class)
    public ResponseEntity<ErrorResponse> handleUnavailable(AccountServiceUnavailableException ex) {
        log.warn("Account service unavailable traceId={} cause={}",
                TraceContext.current(), ex.getCause() != null ? ex.getCause().getMessage() : ex.getMessage());
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(ErrorResponse.of("ACCOUNT_SERVICE_UNAVAILABLE",
                        "Event received but could not be applied: account service is unavailable. " +
                        "It has been stored and can be retried or queried by ID.",
                        TraceContext.current()));
    }

    @ExceptionHandler(DownstreamRejectionException.class)
    public ResponseEntity<ErrorResponse> handleRejection(DownstreamRejectionException ex) {
        log.warn("Downstream rejection traceId={} message={}", TraceContext.current(), ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(ErrorResponse.of("DOWNSTREAM_ERROR", ex.getMessage(), TraceContext.current()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception ex) {
        log.error("Unhandled exception traceId={}", TraceContext.current(), ex);
        return ResponseEntity.internalServerError()
                .body(ErrorResponse.of("INTERNAL_ERROR", "An unexpected error occurred", TraceContext.current()));
    }
}
