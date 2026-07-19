package com.eventledger.account.dto.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record AccountDetailResponse(
    String accountId,
    BigDecimal balance,
    String currency,
    List<TransactionSummary> recentTransactions
) {
    public record TransactionSummary(
        String eventId,
        String type,
        BigDecimal amount,
        Instant eventTimestamp
    ) {}
}
