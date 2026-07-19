package com.eventledger.gateway.client;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.springframework.stereotype.Component;

/**
 * Reads the circuit breaker's current state rather than making a live probe
 * call, so /health stays fast and side-effect-free.
 */
@Component
public class AccountServiceHealthProbe {

    private final CircuitBreakerRegistry registry;

    public AccountServiceHealthProbe(CircuitBreakerRegistry registry) {
        this.registry = registry;
    }

    public boolean isUp() {
        CircuitBreaker cb = registry.circuitBreaker("accountService");
        CircuitBreaker.State state = cb.getState();
        return state == CircuitBreaker.State.CLOSED || state == CircuitBreaker.State.HALF_OPEN;
    }
}
