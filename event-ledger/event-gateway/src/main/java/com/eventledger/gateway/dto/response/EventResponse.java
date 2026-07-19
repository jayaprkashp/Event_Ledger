package com.eventledger.gateway.dto.response;

import com.eventledger.gateway.domain.EventStatus;
import com.eventledger.gateway.domain.TransactionType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

public record EventResponse(
    String eventId,
    String accountId,
    TransactionType type,
    BigDecimal amount,
    String currency,
    Instant eventTimestamp,
    Map<String, Object> metadata,
    EventStatus status,
    BigDecimal balance,
    String balanceCurrency,
    Instant receivedAt
) {}
