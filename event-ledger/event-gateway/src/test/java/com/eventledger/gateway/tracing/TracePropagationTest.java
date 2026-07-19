package com.eventledger.gateway.tracing;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.cloud.contract.wiremock.AutoConfigureWireMock;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWireMock(port = 0)
@ActiveProfiles("test")
class TracePropagationTest {

    @Autowired
    TestRestTemplate restTemplate;

    private Map<String, Object> eventPayload(String eventId) {
        return Map.of(
                "eventId", eventId,
                "accountId", "acct-trace-test",
                "type", "CREDIT",
                "amount", new BigDecimal("10.00"),
                "currency", "USD",
                "eventTimestamp", Instant.parse("2026-05-15T14:00:00Z").toString()
        );
    }

    @Test
    void traceIdGeneratedAtGateway_isForwardedToAccountService_andReturnedToClient() {
        stubFor(post(urlPathMatching("/accounts/.*/transactions"))
                .willReturn(okJson("{\"accountId\":\"acct-trace-test\",\"balance\":100.0,\"currency\":\"USD\"}")));

        var response = restTemplate.postForEntity("/events", eventPayload("evt-trace-1"), Map.class);

        String returnedTraceId = response.getHeaders().getFirst("X-Trace-Id");
        assertThat(returnedTraceId).isNotBlank();

        verify(postRequestedFor(urlPathMatching("/accounts/.*/transactions"))
                .withHeader("X-Trace-Id", equalTo(returnedTraceId)));
    }

    @Test
    void clientSuppliedTraceId_isPreservedRatherThanOverwritten() {
        stubFor(post(urlPathMatching("/accounts/.*/transactions"))
                .willReturn(okJson("{\"accountId\":\"acct-trace-test\",\"balance\":100.0,\"currency\":\"USD\"}")));

        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Trace-Id", "client-supplied-trace-123");
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(eventPayload("evt-trace-2"), headers);

        var response = restTemplate.postForEntity("/events", entity, Map.class);

        assertThat(response.getHeaders().getFirst("X-Trace-Id")).isEqualTo("client-supplied-trace-123");
    }
}
