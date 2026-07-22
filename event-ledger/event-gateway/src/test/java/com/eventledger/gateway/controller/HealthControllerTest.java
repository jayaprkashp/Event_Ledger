package com.eventledger.gateway.controller;

import com.eventledger.gateway.client.AccountServiceHealthProbe;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(HealthController.class)
class HealthControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockBean
    DataSource dataSource;

    @MockBean
    AccountServiceHealthProbe accountServiceHealthProbe;

    @Test
    void databaseUpAndAccountServiceUp_returns200Up() throws Exception {
        Connection connection = mock(Connection.class);
        when(connection.isValid(2)).thenReturn(true);
        when(dataSource.getConnection()).thenReturn(connection);
        when(accountServiceHealthProbe.isUp()).thenReturn(true);

        mockMvc.perform(get("/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.service").value("event-gateway"))
                .andExpect(jsonPath("$.checks.database").value("UP"))
                .andExpect(jsonPath("$.checks.accountService").value("UP"));

        verify(connection).close();
    }

    @Test
    void databaseUpButAccountServiceDown_returns200Degraded() throws Exception {
        // Still a 200 -- the Gateway itself is functional even when the
        // Account Service isn't, per the graceful-degradation design.
        Connection connection = mock(Connection.class);
        when(connection.isValid(2)).thenReturn(true);
        when(dataSource.getConnection()).thenReturn(connection);
        when(accountServiceHealthProbe.isUp()).thenReturn(false);

        mockMvc.perform(get("/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DEGRADED"))
                .andExpect(jsonPath("$.checks.database").value("UP"))
                .andExpect(jsonPath("$.checks.accountService").value("DOWN"));
    }

    @Test
    void databaseDown_returnsDownRegardlessOfAccountServiceState() throws Exception {
        // dbUp=false must win over accountServiceUp, whatever its value --
        // covers the "!dbUp ? DOWN : ..." short-circuit branch.
        when(dataSource.getConnection()).thenThrow(new SQLException("connection refused"));
        when(accountServiceHealthProbe.isUp()).thenReturn(true);

        mockMvc.perform(get("/health"))
                .andExpect(status().isOk()) // still HTTP 200 per current design -- only the body says DOWN
                .andExpect(jsonPath("$.status").value("DOWN"))
                .andExpect(jsonPath("$.checks.database").value("DOWN"));
    }

    @Test
    void connectionObtainedButNotValid_countsAsDatabaseDown() throws Exception {
        // Distinct branch from the exception-thrown case above -- getConnection()
        // succeeds, isValid(2) returns false.
        Connection connection = mock(Connection.class);
        when(connection.isValid(2)).thenReturn(false);
        when(dataSource.getConnection()).thenReturn(connection);
        when(accountServiceHealthProbe.isUp()).thenReturn(true);

        mockMvc.perform(get("/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DOWN"))
                .andExpect(jsonPath("$.checks.database").value("DOWN"));
    }
}
