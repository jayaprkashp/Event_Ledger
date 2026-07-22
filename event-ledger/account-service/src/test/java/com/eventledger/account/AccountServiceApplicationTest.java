package com.eventledger.account;

import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;

/**
 * Covers AccountServiceApplication.main(), which no other test calls -- every
 * other test either uses @SpringBootTest (which boots a context WITHOUT ever
 * executing this literal main method body) or is a plain unit/slice test that
 * never touches this class at all.
 *
 * Deliberately does NOT let this actually start a real Spring context (that
 * would be slow and redundant with the @SpringBootTest-based tests
 * elsewhere) -- SpringApplication.run is statically mocked so we only verify
 * that main() calls it with the right class and arguments, not that a full
 * application boots successfully.
 */
class AccountServiceApplicationTest {

    @Test
    void main_invokesSpringApplicationRun_withThisClassAndTheGivenArgs() {
        String[] args = {"--server.port=0"};
        ConfigurableApplicationContext mockContext = mock(ConfigurableApplicationContext.class);

        try (var springApplicationMock = mockStatic(SpringApplication.class)) {
            springApplicationMock
                    .when(() -> SpringApplication.run(AccountServiceApplication.class, args))
                    .thenReturn(mockContext);

            AccountServiceApplication.main(args);

            springApplicationMock.verify(() -> SpringApplication.run(AccountServiceApplication.class, args));
        }
    }
}
