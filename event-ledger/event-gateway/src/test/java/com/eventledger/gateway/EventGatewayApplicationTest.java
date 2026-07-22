package com.eventledger.gateway;

import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;

/**
 * Covers EventGatewayApplication.main(), which no other test calls -- every
 * other test either uses @SpringBootTest (which boots a context WITHOUT ever
 * executing this literal main method body) or is a plain unit/slice test that
 * never touches this class. See AccountServiceApplicationTest for the same
 * pattern on the other module.
 */
class EventGatewayApplicationTest {

    @Test
    void main_invokesSpringApplicationRun_withThisClassAndTheGivenArgs() {
        String[] args = {"--server.port=0"};
        ConfigurableApplicationContext mockContext = mock(ConfigurableApplicationContext.class);

        try (var springApplicationMock = mockStatic(SpringApplication.class)) {
            springApplicationMock
                    .when(() -> SpringApplication.run(EventGatewayApplication.class, args))
                    .thenReturn(mockContext);

            EventGatewayApplication.main(args);

            springApplicationMock.verify(() -> SpringApplication.run(EventGatewayApplication.class, args));
        }
    }
}
