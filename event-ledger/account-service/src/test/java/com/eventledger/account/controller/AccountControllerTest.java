package com.eventledger.account.controller;

import com.eventledger.account.dto.response.AccountDetailResponse;
import com.eventledger.account.dto.response.BalanceResponse;
import com.eventledger.account.dto.response.TransactionResponse;
import com.eventledger.account.exception.AccountNotFoundException;
import com.eventledger.account.service.AccountTransactionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AccountController.class)
class AccountControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockBean
    AccountTransactionService service;

    // -----------------------------------------------------------------
    // POST /accounts/{accountId}/transactions -- validation (pre-existing)
    // -----------------------------------------------------------------

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

    // -----------------------------------------------------------------
    // POST /accounts/{accountId}/transactions -- success paths (NEW)
    // -----------------------------------------------------------------

    @Test
    void newTransaction_returns201Created_withTransactionResponseBody() throws Exception {
        var applied = new TransactionResponse.AppliedTransaction(
                "evt-new", "CREDIT", new BigDecimal("100.00"), Instant.parse("2026-05-15T14:00:00Z"));
        var responseBody = new TransactionResponse(
                "acct-1", new BigDecimal("100.00"), "USD", Instant.now(), applied);

        when(service.transactionExists("evt-new")).thenReturn(false);
        when(service.applyTransaction(eq("acct-1"), any())).thenReturn(responseBody);

        mockMvc.perform(post("/accounts/acct-1/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"eventId\":\"evt-new\",\"type\":\"CREDIT\",\"amount\":100.00,\"currency\":\"USD\",\"eventTimestamp\":\"2026-05-15T14:00:00Z\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.accountId").value("acct-1"))
                .andExpect(jsonPath("$.balance").value(100.00))
                .andExpect(jsonPath("$.appliedTransaction.eventId").value("evt-new"));

        verify(service).transactionExists("evt-new");
        verify(service).applyTransaction(eq("acct-1"), any());
    }

    @Test
    void duplicateTransaction_returns200OK_notCreated() throws Exception {
        var applied = new TransactionResponse.AppliedTransaction(
                "evt-dup", "CREDIT", new BigDecimal("50.00"), Instant.parse("2026-05-15T14:00:00Z"));
        var responseBody = new TransactionResponse(
                "acct-1", new BigDecimal("50.00"), "USD", Instant.now(), applied);

        when(service.transactionExists("evt-dup")).thenReturn(true);
        when(service.applyTransaction(eq("acct-1"), any())).thenReturn(responseBody);

        mockMvc.perform(post("/accounts/acct-1/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"eventId\":\"evt-dup\",\"type\":\"CREDIT\",\"amount\":50.00,\"currency\":\"USD\",\"eventTimestamp\":\"2026-05-15T14:00:00Z\"}"))
                .andExpect(status().isOk()) // 200, not 201, per the alreadyExisted branch
                .andExpect(jsonPath("$.balance").value(50.00));
    }

    // -----------------------------------------------------------------
    // GET /accounts/{accountId}/balance
    // -----------------------------------------------------------------

    @Test
    void getBalance_existingAccount_returns200() throws Exception {
        when(service.getBalance("acct-1"))
                .thenReturn(new BalanceResponse("acct-1", new BigDecimal("250.00"), "USD", Instant.now()));

        mockMvc.perform(get("/accounts/acct-1/balance"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountId").value("acct-1"))
                .andExpect(jsonPath("$.balance").value(250.00))
                .andExpect(jsonPath("$.currency").value("USD"));
    }

    @Test
    void getBalance_nonExistentAccount_returns404() throws Exception {
        when(service.getBalance("acct-missing"))
                .thenThrow(new AccountNotFoundException("acct-missing"));

        mockMvc.perform(get("/accounts/acct-missing/balance"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("ACCOUNT_NOT_FOUND"));
    }

    // -----------------------------------------------------------------
    // GET /accounts/{accountId}
    // -----------------------------------------------------------------

    @Test
    void getAccountDetail_existingAccount_returns200() throws Exception {
        var summary = new AccountDetailResponse.TransactionSummary(
                "evt-1", "CREDIT", new BigDecimal("100.00"), Instant.parse("2026-05-15T14:00:00Z"));
        when(service.getAccountDetail("acct-1"))
                .thenReturn(new AccountDetailResponse("acct-1", new BigDecimal("100.00"), "USD", List.of(summary)));

        mockMvc.perform(get("/accounts/acct-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountId").value("acct-1"))
                .andExpect(jsonPath("$.recentTransactions[0].eventId").value("evt-1"));
    }

    @Test
    void getAccountDetail_nonExistentAccount_returns404() throws Exception {
        when(service.getAccountDetail("acct-missing"))
                .thenThrow(new AccountNotFoundException("acct-missing"));

        mockMvc.perform(get("/accounts/acct-missing"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("ACCOUNT_NOT_FOUND"));
    }
}
