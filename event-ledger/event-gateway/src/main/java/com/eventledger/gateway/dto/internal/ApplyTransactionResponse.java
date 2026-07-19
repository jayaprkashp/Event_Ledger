package com.eventledger.gateway.dto.internal;

import java.math.BigDecimal;

public record ApplyTransactionResponse(
    String accountId,
    BigDecimal balance,
    String currency
) {}
