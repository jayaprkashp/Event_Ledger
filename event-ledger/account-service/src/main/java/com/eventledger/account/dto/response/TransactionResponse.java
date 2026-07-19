package com.eventledger.account.dto.response;

import java.math.BigDecimal;
import java.time.Instant;

public record TransactionResponse(
    String accountId,
    BigDecimal balance,
    String currency,
    Instant asOf,
    AppliedTransaction appliedTransaction
) {
    public record AppliedTransaction(
        String eventId,
        String type,
        BigDecimal amount,
        Instant eventTimestamp
    ) {}
}
