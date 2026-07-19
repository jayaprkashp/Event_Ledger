package com.eventledger.account.service;

import com.eventledger.account.dto.request.ApplyTransactionRequest;
import com.eventledger.account.dto.response.AccountDetailResponse;
import com.eventledger.account.repository.AccountRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Proves the core correctness claim of the whole system: balance is
 * identical regardless of the order transactions are applied in, and
 * transaction history is listed by event_timestamp, not application order.
 */
@SpringBootTest
@ActiveProfiles("test")
class OutOfOrderBalanceTest {

    @Autowired
    AccountTransactionService service;

    @Autowired
    AccountRepository accountRepository;

    private void applyCredit(String accountId, String eventId, String amount, String isoTimestamp) {
        service.applyTransaction(accountId, new ApplyTransactionRequest(
                eventId, "CREDIT", new BigDecimal(amount), "USD", Instant.parse(isoTimestamp)));
    }

    @Test
    void balance_isIdentical_regardlessOfArrivalOrder() {
        // Scenario 1: earlier event applied first (natural order)
        applyCredit("acct-order-a", "evt-1", "100.00", "2026-05-15T14:00:00Z");
        applyCredit("acct-order-a", "evt-2", "50.00", "2026-05-15T14:05:00Z");
        BigDecimal naturalOrderBalance = accountRepository.findByAccountId("acct-order-a").orElseThrow().getBalance();

        // Scenario 2: same two events, later one applied first (out of order)
        applyCredit("acct-order-b", "evt-3", "50.00", "2026-05-15T14:05:00Z");
        applyCredit("acct-order-b", "evt-4", "100.00", "2026-05-15T14:00:00Z");
        BigDecimal reversedOrderBalance = accountRepository.findByAccountId("acct-order-b").orElseThrow().getBalance();

        assertThat(naturalOrderBalance).isEqualByComparingTo(reversedOrderBalance);
        assertThat(naturalOrderBalance).isEqualByComparingTo("150.00");
    }

    @Test
    void transactionHistory_listedByEventTimestampDescending_notApplicationOrder() {
        applyCredit("acct-hist", "evt-later", "50.00", "2026-05-15T14:05:00Z");   // applied first
        applyCredit("acct-hist", "evt-earlier", "100.00", "2026-05-15T14:00:00Z"); // applied second

        AccountDetailResponse detail = service.getAccountDetail("acct-hist");

        assertThat(detail.recentTransactions())
                .extracting(AccountDetailResponse.TransactionSummary::eventId)
                .containsExactly("evt-later", "evt-earlier"); // descending by event_timestamp
    }
}
