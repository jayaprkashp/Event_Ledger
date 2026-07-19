package com.eventledger.account.controller;

import com.eventledger.account.service.AccountTransactionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AccountController.class)
class AccountControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockBean
    AccountTransactionService service;

    @Test
    void negativeAmount_returns400_withFieldSpecificMessage() throws Exception {
        mockMvc.perform(post("/accounts/acct-1/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"eventId\":\"e1\",\"type\":\"CREDIT\",\"amount\":-5,\"currency\":\"USD\",\"eventTimestamp\":\"2026-05-15T14:00:00Z\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.error.message").value(containsString("amount")));
    }

    @Test
    void unknownType_returns400() throws Exception {
        mockMvc.perform(post("/accounts/acct-1/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"eventId\":\"e2\",\"type\":\"TRANSFER\",\"amount\":10,\"currency\":\"USD\",\"eventTimestamp\":\"2026-05-15T14:00:00Z\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void missingEventId_returns400() throws Exception {
        mockMvc.perform(post("/accounts/acct-1/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"CREDIT\",\"amount\":10,\"currency\":\"USD\",\"eventTimestamp\":\"2026-05-15T14:00:00Z\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.message").value(containsString("eventId")));
    }
}
