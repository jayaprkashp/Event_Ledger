package com.eventledger.account.service;

import com.eventledger.account.domain.Account;
import com.eventledger.account.domain.AccountTransaction;
import com.eventledger.account.domain.TransactionType;
import com.eventledger.account.dto.request.ApplyTransactionRequest;
import com.eventledger.account.repository.AccountRepository;
import com.eventledger.account.repository.AccountTransactionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AccountTransactionServiceTest {

    @Mock AccountRepository accountRepository;
    @Mock AccountTransactionRepository transactionRepository;
    @InjectMocks AccountTransactionService service;

    @Test
    void duplicateEventId_doesNotMutateBalance_returnsExistingState() {
        AccountTransaction existingTxn = new AccountTransaction("evt-dup", "acct-1", TransactionType.CREDIT,
                new BigDecimal("100.00"), Instant.now());
        when(transactionRepository.findByEventId("evt-dup")).thenReturn(Optional.of(existingTxn));
        Account account = new Account("acct-1", "USD");
        when(accountRepository.findByAccountId("acct-1")).thenReturn(Optional.of(account));

        service.applyTransaction("acct-1", new ApplyTransactionRequest(
                "evt-dup", "CREDIT", new BigDecimal("100.00"), "USD", Instant.now()));

        verify(accountRepository, never()).findWithLockByAccountId(any());
        verify(transactionRepository, never()).save(any());
    }

    @Test
    void newTransaction_creditIncreasesBalance() {
        when(transactionRepository.findByEventId("evt-new")).thenReturn(Optional.empty());
        Account account = new Account("acct-2", "USD");
        when(accountRepository.findWithLockByAccountId("acct-2")).thenReturn(Optional.of(account));
        when(accountRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(transactionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.applyTransaction("acct-2", new ApplyTransactionRequest(
                "evt-new", "CREDIT", new BigDecimal("75.00"), "USD", Instant.now()));

        verify(accountRepository).save(argThat(a -> a.getBalance().compareTo(new BigDecimal("75.00")) == 0));
    }
}
