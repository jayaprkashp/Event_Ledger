package com.eventledger.account.controller;

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

    @Test
    void databaseReachableAndValid_returns200Up() throws Exception {
        Connection connection = mock(Connection.class);
        when(connection.isValid(2)).thenReturn(true);
        when(dataSource.getConnection()).thenReturn(connection);

        mockMvc.perform(get("/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.service").value("account-service"))
                .andExpect(jsonPath("$.checks.database").value("UP"));

        verify(connection).close(); // try-with-resources must close the connection either way
    }

    @Test
    void connectionObtainedButNotValid_returns503Down() throws Exception {
        // Covers the branch where getConnection() succeeds but isValid(2)
        // returns false -- distinct from the exception-thrown branch below.
        Connection connection = mock(Connection.class);
        when(connection.isValid(2)).thenReturn(false);
        when(dataSource.getConnection()).thenReturn(connection);

        mockMvc.perform(get("/health"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value("DOWN"))
                .andExpect(jsonPath("$.checks.database").value("DOWN"));
    }

    @Test
    void getConnectionThrows_returns503Down() throws Exception {
        // Covers the catch (Exception e) branch -- e.g. the DB is entirely
        // unreachable, not just momentarily invalid.
        when(dataSource.getConnection()).thenThrow(new SQLException("connection refused"));

        mockMvc.perform(get("/health"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value("DOWN"))
                .andExpect(jsonPath("$.service").value("account-service"))
                .andExpect(jsonPath("$.checks.database").value("DOWN"));
    }
}
