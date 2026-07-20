package com.eventledger.gateway.client;

import com.eventledger.gateway.dto.internal.ApplyTransactionRequest;
import com.eventledger.gateway.dto.internal.ApplyTransactionResponse;
import org.springframework.stereotype.Component;

/**
 * Thin adapter used by EventService. The actual Resilience4j-wrapped call
 * lives on ResilientAccountServiceInvoker, a SEPARATE bean -- calling it from
 * here crosses a real Spring AOP proxy boundary, which is what makes
 * @Retry/@CircuitBreaker actually engage. (Putting the annotated method
 * directly on this class and calling it via `this.` is a classic Spring AOP
 * self-invocation bug: the proxy never intercepts a same-bean call, so the
 * annotations silently do nothing and the circuit breaker's state never
 * leaves CLOSED.)
 */
@Component
public class AccountServiceClient {

    private final ResilientAccountServiceInvoker invoker;

    public AccountServiceClient(ResilientAccountServiceInvoker invoker) {
        this.invoker = invoker;
    }

    public ApplyTransactionResponse applyTransaction(String accountId, ApplyTransactionRequest request, String traceId) {
        return invoker.applyTransaction(accountId, request, traceId);
    }
}
