package com.eventledger.gateway.resiliency;

import com.eventledger.gateway.dto.response.EventResponse;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.cloud.contract.wiremock.AutoConfigureWireMock;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWireMock(port = 0)
@ActiveProfiles("test")
class CircuitBreakerTest {

    @Autowired
    TestRestTemplate restTemplate;

    @Autowired
    CircuitBreakerRegistry circuitBreakerRegistry;

    private Map<String, Object> eventPayload(String eventId) {
        return Map.of(
                "eventId", eventId,
                "accountId", "acct-cb-test",
                "type", "CREDIT",
                "amount", new BigDecimal("10.00"),
                "currency", "USD",
                "eventTimestamp", Instant.parse("2026-05-15T14:00:00Z").toString()
        );
    }

    @Test
    void repeatedFailures_openCircuit_subsequentCallsFailFast_withoutHittingStub() {
        stubFor(post(urlPathMatching("/accounts/.*/transactions"))
                .willReturn(aResponse().withStatus(500)));

        for (int i = 0; i < 6; i++) {
            restTemplate.postForEntity("/events", eventPayload("evt-cb-fail-" + i), EventResponse.class);
        }

        CircuitBreaker breaker = circuitBreakerRegistry.circuitBreaker("accountService");
        assertThat(breaker.getState()).isEqualTo(CircuitBreaker.State.OPEN);

        int callsBefore = findAll(postRequestedFor(urlPathMatching("/accounts/.*/transactions"))).size();
        var response = restTemplate.postForEntity("/events", eventPayload("evt-cb-after-open"), Map.class);
        int callsAfter = findAll(postRequestedFor(urlPathMatching("/accounts/.*/transactions"))).size();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(callsAfter).isEqualTo(callsBefore); // breaker short-circuited -- no new call reached the stub
    }

    @Test
    void readEndpoints_stillWork_whileAccountServiceUnreachable() {
        stubFor(post(urlPathMatching("/accounts/.*/transactions"))
                .willReturn(aResponse().withStatus(500)));

        for (int i = 0; i < 6; i++) {
            restTemplate.postForEntity("/events", eventPayload("evt-read-" + i), EventResponse.class);
        }

        var response = restTemplate.getForEntity("/events?account=acct-cb-test", Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }
}
