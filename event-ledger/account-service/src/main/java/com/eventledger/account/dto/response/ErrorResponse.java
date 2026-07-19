package com.eventledger.account.dto.response;

import java.time.Instant;

public record ErrorResponse(ErrorBody error) {

    public record ErrorBody(
        String code,
        String message,
        String traceId,
        Instant timestamp
    ) {}

    public static ErrorResponse of(String code, String message, String traceId) {
        return new ErrorResponse(new ErrorBody(code, message, traceId, Instant.now()));
    }
}
