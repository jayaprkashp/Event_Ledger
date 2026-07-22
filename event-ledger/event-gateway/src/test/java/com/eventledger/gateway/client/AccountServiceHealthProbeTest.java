package com.eventledger.gateway.client;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AccountServiceHealthProbeTest {

    @Test
    void closedState_isUp() {
        assertThat(isUpForState(CircuitBreaker.State.CLOSED)).isTrue();
    }

    @Test
    void halfOpenState_isUp() {
        assertThat(isUpForState(CircuitBreaker.State.HALF_OPEN)).isTrue();
    }

    @Test
    void openState_isNotUp() {
        assertThat(isUpForState(CircuitBreaker.State.OPEN)).isFalse();
    }

    private boolean isUpForState(CircuitBreaker.State state) {
        CircuitBreaker breaker = mock(CircuitBreaker.class);
        when(breaker.getState()).thenReturn(state);

        CircuitBreakerRegistry registry = mock(CircuitBreakerRegistry.class);
        when(registry.circuitBreaker("accountService")).thenReturn(breaker);

        AccountServiceHealthProbe probe = new AccountServiceHealthProbe(registry);
        return probe.isUp();
    }
}
