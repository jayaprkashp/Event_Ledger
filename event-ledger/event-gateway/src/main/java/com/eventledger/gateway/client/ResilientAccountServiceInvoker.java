package com.eventledger.gateway.client;

import com.eventledger.gateway.dto.internal.ApplyTransactionRequest;
import com.eventledger.gateway.dto.internal.ApplyTransactionResponse;
import com.eventledger.gateway.dto.response.ErrorResponse;
import com.eventledger.gateway.exception.AccountServiceUnavailableException;
import com.eventledger.gateway.exception.DownstreamRejectionException;
import com.eventledger.gateway.exception.RetryableDownstreamException;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

/**
 * Holds the Resilience4j-annotated call to the Account Service, deliberately
 * kept on a DEDICATED bean, separate from {@link AccountServiceClient} --
 * calling it from a different bean is required for Spring AOP's proxy to
 * actually intercept the call (see AccountServiceClient's Javadoc for the
 * self-invocation pitfall this avoids).
 *
 * Deliberately SYNCHRONOUS (no @TimeLimiter / CompletableFuture): the
 * downstream call is fundamentally blocking, and @TimeLimiter's
 * CompletableFuture-based composition adds real fragility (it needs precise
 * executor/scheduling wiring to behave predictably alongside @Retry) without
 * adding anything here -- the RestClient's own connect/read timeouts
 * (RestClientConfig) already enforce the timeout at the transport level.
 * @Retry + @CircuitBreaker on a plain synchronous method is the far more
 * common, better-tested Resilience4j usage pattern.
 */
@Component
public class ResilientAccountServiceInvoker {

    private final RestClient restClient;

    public ResilientAccountServiceInvoker(RestClient accountServiceRestClient) {
        this.restClient = accountServiceRestClient;
    }

    // Retry wraps CircuitBreaker (Resilience4j's default aspect order) -- a
    // retried call still counts as a separate attempt against the breaker's
    // sliding window, so a genuine outage still trips it correctly.
    @Retry(name = "accountService")
    @CircuitBreaker(name = "accountService", fallbackMethod = "fallback")
    public ApplyTransactionResponse applyTransaction(String accountId, ApplyTransactionRequest request, String traceId) {
        try {
            return restClient.post()
                    .uri("/accounts/{accountId}/transactions", accountId)
                    .header("X-Trace-Id", traceId)
                    .body(request)
                    .retrieve()
                    .body(ApplyTransactionResponse.class);

        } catch (HttpClientErrorException.Conflict ex) { // 409 -- transient concurrency conflict, retryable
            throw new RetryableDownstreamException("Concurrent update at account service, retrying", ex);

        } catch (ResourceAccessException ex) { // connection refused / timeout at transport level
            throw new RetryableDownstreamException("Account service unreachable", ex);

        } catch (HttpClientErrorException ex) { // other 4xx -- a genuine rejection, not retryable
            throw new DownstreamRejectionException(extractMessage(ex));

        } catch (HttpServerErrorException ex) { // 5xx from account service itself
            throw new RetryableDownstreamException("Account service returned a server error", ex);
        }
    }

    @SuppressWarnings("unused") // invoked reflectively by Resilience4j as the fallbackMethod
    private ApplyTransactionResponse fallback(
            String accountId, ApplyTransactionRequest request, String traceId, Throwable t) {
        if (t instanceof CallNotPermittedException) {
        	System.out.println("Jp account service invoker fallback - circuit breaker open");
            throw new AccountServiceUnavailableException("Circuit breaker open for account service", t);
        }
        if (t instanceof RuntimeException re) {
            throw re;
        }
        throw new AccountServiceUnavailableException("Account service call failed", t);
    }

    private String extractMessage(HttpClientErrorException ex) {
        try {
            ErrorResponse body = ex.getResponseBodyAs(ErrorResponse.class);
            return body != null ? body.error().message() : ex.getMessage();
        } catch (Exception parseFailure) {
            return ex.getMessage();
        }
    }
}
