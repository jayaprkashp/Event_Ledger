package com.eventledger.gateway.client;

import com.eventledger.gateway.dto.internal.ApplyTransactionRequest;
import com.eventledger.gateway.dto.internal.ApplyTransactionResponse;
import com.eventledger.gateway.domain.TransactionType;
import com.eventledger.gateway.exception.AccountServiceUnavailableException;
import com.eventledger.gateway.exception.DownstreamRejectionException;
import com.eventledger.gateway.exception.RetryableDownstreamException;
import com.github.tomakehurst.wiremock.WireMockServer;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.time.Instant;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Bypasses Spring/AOP entirely -- this instantiates ResilientAccountServiceInvoker
 * as a raw POJO against a hand-started WireMock server, so @Retry/@CircuitBreaker
 * are never actually active here. That's deliberate: the goal is to cover each
 * individual catch branch in doApply()/extractMessage() with a specific HTTP
 * response, which is awkward to force reliably through the full Resilience4j
 * stack (retry/breaker timing gets in the way). The resiliency BEHAVIOR itself
 * (retry, circuit breaker state transitions) is covered separately by
 * CircuitBreakerTest and TracePropagationTest, which DO go through the real
 * Spring-proxied bean.
 */
class ResilientAccountServiceInvokerTest {

    private WireMockServer wireMockServer;
    private ResilientAccountServiceInvoker invoker;

    @BeforeEach
    void setUp() {
        wireMockServer = new WireMockServer(options().dynamicPort());
        wireMockServer.start();
        RestClient restClient = RestClient.builder()
                .baseUrl("http://localhost:" + wireMockServer.port())
                .build();
        invoker = new ResilientAccountServiceInvoker(restClient);
    }

    @AfterEach
    void tearDown() {
        wireMockServer.stop();
    }

    private ApplyTransactionRequest sampleRequest() {
        return new ApplyTransactionRequest(
                "evt-1", TransactionType.CREDIT, new BigDecimal("10.00"), "USD", Instant.now());
    }

    @Test
    void successfulCall_returnsParsedResponse() {
        wireMockServer.stubFor(post(urlPathMatching("/accounts/.*/transactions"))
                .willReturn(okJson("{\"accountId\":\"acct-1\",\"balance\":110.00,\"currency\":\"USD\"}")));

        ApplyTransactionResponse response = invoker.applyTransaction("acct-1", sampleRequest(), "trace-1");

        assertThat(response.accountId()).isEqualTo("acct-1");
        assertThat(response.balance()).isEqualByComparingTo("110.00");
    }

    @Test
    void conflict409_throwsRetryableDownstreamException() {
        wireMockServer.stubFor(post(urlPathMatching("/accounts/.*/transactions"))
                .willReturn(aResponse().withStatus(409)));

        assertThatThrownBy(() -> invoker.applyTransaction("acct-1", sampleRequest(), "trace-1"))
                .isInstanceOf(RetryableDownstreamException.class)
                .hasMessageContaining("Concurrent update");
    }

    @Test
    void serverError5xx_throwsRetryableDownstreamException() {
        wireMockServer.stubFor(post(urlPathMatching("/accounts/.*/transactions"))
                .willReturn(aResponse().withStatus(500)));

        assertThatThrownBy(() -> invoker.applyTransaction("acct-1", sampleRequest(), "trace-1"))
                .isInstanceOf(RetryableDownstreamException.class)
                .hasMessageContaining("server error");
    }

    @Test
    void otherClientError4xx_withParseableErrorBody_throwsDownstreamRejectionWithParsedMessage() {
        wireMockServer.stubFor(post(urlPathMatching("/accounts/.*/transactions"))
                .willReturn(aResponse().withStatus(400).withHeader("Content-Type", "application/json")
                        .withBody("{\"error\":{\"code\":\"VALIDATION_ERROR\",\"message\":\"amount must be greater than 0\",\"traceId\":\"t1\",\"timestamp\":\"2026-05-15T14:00:00Z\"}}")));

        assertThatThrownBy(() -> invoker.applyTransaction("acct-1", sampleRequest(), "trace-1"))
                .isInstanceOf(DownstreamRejectionException.class)
                .hasMessage("amount must be greater than 0");
    }

    @Test
    void otherClientError4xx_withUnparseableBody_fallsBackToRawExceptionMessage() {
        // Covers extractMessage's catch(parseFailure) branch -- body isn't
        // valid JSON matching ErrorResponse's shape at all.
        wireMockServer.stubFor(post(urlPathMatching("/accounts/.*/transactions"))
                .willReturn(aResponse().withStatus(404).withBody("not json at all")));

        assertThatThrownBy(() -> invoker.applyTransaction("acct-1", sampleRequest(), "trace-1"))
                .isInstanceOf(DownstreamRejectionException.class);
        // Message content here comes from HttpClientErrorException's own
        // getMessage(), which is fine to leave unasserted on exact text --
        // what matters is it didn't throw while trying to parse the body.
    }

    @Test
    void connectionRefused_throwsRetryableDownstreamException() {
        RestClient unreachable = RestClient.builder()
                .baseUrl("http://localhost:1") // reserved port, nothing listens here
                .build();
        ResilientAccountServiceInvoker unreachableInvoker = new ResilientAccountServiceInvoker(unreachable);

        assertThatThrownBy(() -> unreachableInvoker.applyTransaction("acct-1", sampleRequest(), "trace-1"))
                .isInstanceOf(RetryableDownstreamException.class)
                .hasMessageContaining("unreachable");
    }

    // -----------------------------------------------------------------
    // fallback() is private and only invoked reflectively by Resilience4j
    // at runtime through the Spring proxy (see CircuitBreakerTest for that
    // path). Calling it directly via reflection here guarantees all three
    // of its branches are covered regardless of the full-context tests'
    // exact timing/loop behavior.
    // -----------------------------------------------------------------

    private Object invokeFallback(Throwable cause) throws Exception {
        Method fallback = ResilientAccountServiceInvoker.class.getDeclaredMethod(
                "fallback", String.class, ApplyTransactionRequest.class, String.class, Throwable.class);
        fallback.setAccessible(true);
        try {
            return fallback.invoke(invoker, "acct-1", sampleRequest(), "trace-1", cause);
        } catch (java.lang.reflect.InvocationTargetException e) {
            throw (Exception) e.getCause();
        }
    }

    @Test
    void fallback_callNotPermitted_throwsAccountServiceUnavailable() {
        io.github.resilience4j.circuitbreaker.CircuitBreaker breaker =
                io.github.resilience4j.circuitbreaker.CircuitBreaker.of(
                        "test", io.github.resilience4j.circuitbreaker.CircuitBreakerConfig.ofDefaults());
        CallNotPermittedException notPermitted = CallNotPermittedException.createCallNotPermittedException(breaker);

        assertThatThrownBy(() -> invokeFallback(notPermitted))
                .isInstanceOf(AccountServiceUnavailableException.class)
                .hasMessageContaining("Circuit breaker open");
    }

    @Test
    void fallback_otherRuntimeException_rethrowsAsIs() {
        RetryableDownstreamException original = new RetryableDownstreamException("boom", null);

        assertThatThrownBy(() -> invokeFallback(original))
                .isSameAs(original); // rethrown unchanged, not wrapped
    }

    @Test
    void fallback_nonRuntimeThrowable_wrapsInAccountServiceUnavailable() {
        Exception checked = new Exception("checked failure");

        assertThatThrownBy(() -> invokeFallback(checked))
                .isInstanceOf(AccountServiceUnavailableException.class)
                .hasMessageContaining("Account service call failed");
    }
}
