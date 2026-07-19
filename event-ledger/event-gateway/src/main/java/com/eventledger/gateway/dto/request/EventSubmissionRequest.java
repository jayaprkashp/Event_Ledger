package com.eventledger.gateway.dto.request;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

public record EventSubmissionRequest(

    @NotBlank(message = "eventId is required")
    String eventId,

    @NotBlank(message = "accountId is required")
    String accountId,

    @NotBlank(message = "type is required")
    @Pattern(regexp = "CREDIT|DEBIT", message = "type must be CREDIT or DEBIT")
    String type,

    @NotNull(message = "amount is required")
    @DecimalMin(value = "0.0", inclusive = false, message = "amount must be greater than 0")
    BigDecimal amount,

    @NotBlank(message = "currency is required")
    @Size(min = 3, max = 3, message = "currency must be a 3-letter code")
    String currency,

    @NotNull(message = "eventTimestamp is required")
    Instant eventTimestamp,

    Map<String, Object> metadata
) {}
