package com.eventledger.gateway.client;

import com.eventledger.gateway.domain.TransactionType;
import com.eventledger.gateway.dto.internal.ApplyTransactionRequest;
import com.eventledger.gateway.dto.internal.ApplyTransactionResponse;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AccountServiceClientTest {

    @Test
    void applyTransaction_delegatesToInvoker_andReturnsItsResult() {
        ResilientAccountServiceInvoker invoker = mock(ResilientAccountServiceInvoker.class);
        ApplyTransactionRequest request = new ApplyTransactionRequest(
                "evt-1", TransactionType.CREDIT, new BigDecimal("10.00"), "USD", Instant.now());
        ApplyTransactionResponse expected = new ApplyTransactionResponse("acct-1", new BigDecimal("110.00"), "USD");

        when(invoker.applyTransaction(eq("acct-1"), eq(request), eq("trace-1"))).thenReturn(expected);

        AccountServiceClient client = new AccountServiceClient(invoker);
        ApplyTransactionResponse actual = client.applyTransaction("acct-1", request, "trace-1");

        assertThat(actual).isSameAs(expected);
        verify(invoker).applyTransaction("acct-1", request, "trace-1");
    }
}
