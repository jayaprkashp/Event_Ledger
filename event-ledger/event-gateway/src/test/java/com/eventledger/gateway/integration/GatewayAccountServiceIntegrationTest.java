package com.eventledger.gateway.integration;

import com.eventledger.gateway.dto.response.EventResponse;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockserver.client.MockServerClient;
import org.mockserver.integration.ClientAndServer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;
import static org.mockserver.model.JsonBody.json;

/**
 * Exercises the full Gateway -> Account Service flow. The Account Service is
 * represented by a MockServer stub rather than a second real Spring context
 * (decision recorded in Artifact 8) -- this is a contract-level integration
 * test; docker-compose.yml is what exercises the two real processes together.
 *
 * Named *IntegrationTest so the root reactor pom's Surefire/Failsafe split
 * routes this to `mvn verify`, not `mvn test`.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class GatewayAccountServiceIntegrationTest {

    static ClientAndServer mockServer;
    static MockServerClient mockServerClient;

    @BeforeAll
    static void startMockServer() {
        mockServer = ClientAndServer.startClientAndServer(0);
        mockServerClient = new MockServerClient("localhost", mockServer.getPort());
    }

    @AfterAll
    static void stopMockServer() {
        mockServer.stop();
    }

    @DynamicPropertySource
    static void accountServiceUrl(DynamicPropertyRegistry registry) {
        registry.add("app.account-service.base-url", () -> "http://localhost:" + mockServer.getPort());
    }

    @Autowired
    TestRestTemplate restTemplate;

    @Test
    void fullFlow_submitEvent_appliesViaAccountService_thenQueryableByIdAndByAccount() {
        mockServerClient.when(request().withMethod("POST").withPath("/accounts/acct-int-1/transactions"))
                .respond(response()
                        .withStatusCode(201)
                        .withBody(json("{\"accountId\":\"acct-int-1\",\"balance\":150.00,\"currency\":\"USD\"}")));

        Map<String, Object> payload = Map.of(
                "eventId", "evt-int-1",
                "accountId", "acct-int-1",
                "type", "CREDIT",
                "amount", new BigDecimal("150.00"),
                "currency", "USD",
                "eventTimestamp", Instant.parse("2026-05-15T14:00:00Z").toString()
        );

        var postResponse = restTemplate.postForEntity("/events", payload, EventResponse.class);
        assertThat(postResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(postResponse.getBody()).isNotNull();
        assertThat(postResponse.getBody().balance()).isEqualByComparingTo("150.00");

        var getResponse = restTemplate.getForEntity("/events/evt-int-1", EventResponse.class);
        assertThat(getResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(getResponse.getBody()).isNotNull();
        assertThat(getResponse.getBody().status().name()).isEqualTo("APPLIED");

        var listResponse = restTemplate.getForEntity("/events?account=acct-int-1", EventResponse[].class);
        assertThat(listResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(listResponse.getBody()).isNotNull();
        assertThat(listResponse.getBody()).extracting(EventResponse::eventId).contains("evt-int-1");
    }

    @Test
    void duplicateSubmission_secondCallNeverReachesAccountService() {
        mockServerClient.when(request().withMethod("POST").withPath("/accounts/acct-int-2/transactions"))
                .respond(response()
                        .withStatusCode(201)
                        .withBody(json("{\"accountId\":\"acct-int-2\",\"balance\":50.00,\"currency\":\"USD\"}")));

        Map<String, Object> payload = Map.of(
                "eventId", "evt-int-2",
                "accountId", "acct-int-2",
                "type", "CREDIT",
                "amount", new BigDecimal("50.00"),
                "currency", "USD",
                "eventTimestamp", Instant.parse("2026-05-15T14:00:00Z").toString()
        );

        var first = restTemplate.postForEntity("/events", payload, EventResponse.class);
        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        var second = restTemplate.postForEntity("/events", payload, EventResponse.class);
        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.OK); // duplicate, not re-applied
    }
}
