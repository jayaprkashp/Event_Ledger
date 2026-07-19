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
import io.github.resilience4j.timelimiter.annotation.TimeLimiter;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

/**
 * Wraps the outbound REST call to the Account Service with Resilience4j's
 * circuit breaker, retry, and timeout, per the resiliency requirement.
 * Annotation order matters: Retry wraps CircuitBreaker wraps TimeLimiter --
 * a retried call still counts as a separate attempt against the breaker's
 * sliding window, so a genuine outage still trips it correctly.
 */
@Component
public class AccountServiceClient {

    private final RestClient restClient;

    public AccountServiceClient(RestClient accountServiceRestClient) {
        this.restClient = accountServiceRestClient;
    }

    @Retry(name = "accountService")
    @CircuitBreaker(name = "accountService", fallbackMethod = "fallback")
    @TimeLimiter(name = "accountService")
    public CompletableFuture<ApplyTransactionResponse> applyTransactionAsync(
            String accountId, ApplyTransactionRequest request, String traceId) {
        return CompletableFuture.supplyAsync(() -> doApply(accountId, request, traceId));
    }

    public ApplyTransactionResponse applyTransaction(String accountId, ApplyTransactionRequest request, String traceId) {
        try {
            return applyTransactionAsync(accountId, request, traceId).join();
        } catch (CompletionException ex) {
            if (ex.getCause() instanceof RuntimeException re) {
                throw re;
            }
            throw new AccountServiceUnavailableException("Unexpected error calling account service", ex);
        }
    }

    private ApplyTransactionResponse doApply(String accountId, ApplyTransactionRequest request, String traceId) {
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
    private CompletableFuture<ApplyTransactionResponse> fallback(
            String accountId, ApplyTransactionRequest request, String traceId, Throwable t) {
        if (t instanceof CallNotPermittedException) {
            return CompletableFuture.failedFuture(
                    new AccountServiceUnavailableException("Circuit breaker open for account service", t));
        }
        return CompletableFuture.failedFuture(
                t instanceof RuntimeException re ? re : new AccountServiceUnavailableException("Account service call failed", t));
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
