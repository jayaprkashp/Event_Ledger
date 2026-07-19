package com.eventledger.account.service;

import com.eventledger.account.domain.Account;
import com.eventledger.account.domain.AccountTransaction;
import com.eventledger.account.domain.TransactionType;
import com.eventledger.account.dto.request.ApplyTransactionRequest;
import com.eventledger.account.dto.response.AccountDetailResponse;
import com.eventledger.account.dto.response.BalanceResponse;
import com.eventledger.account.dto.response.TransactionResponse;
import com.eventledger.account.exception.AccountNotFoundException;
import com.eventledger.account.repository.AccountRepository;
import com.eventledger.account.repository.AccountTransactionRepository;
import com.eventledger.account.tracing.TraceContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Service
public class AccountTransactionService {

    private static final Logger log = LoggerFactory.getLogger(AccountTransactionService.class);
    private static final int RECENT_TRANSACTIONS_LIMIT = 50;

    private final AccountRepository accountRepository;
    private final AccountTransactionRepository transactionRepository;

    public AccountTransactionService(AccountRepository accountRepository,
                                      AccountTransactionRepository transactionRepository) {
        this.accountRepository = accountRepository;
        this.transactionRepository = transactionRepository;
    }

    public boolean transactionExists(String eventId) {
        return transactionRepository.existsByEventId(eventId);
    }

    @Transactional
    public TransactionResponse applyTransaction(String accountId, ApplyTransactionRequest request) {
        // Defense-in-depth idempotency check -- even if the Gateway retried a
        // call that actually succeeded, this stops a second balance mutation.
        Optional<AccountTransaction> existing = transactionRepository.findByEventId(request.eventId());
        if (existing.isPresent()) {
            log.info("Duplicate transaction at account-service layer eventId={} traceId={}",
                    request.eventId(), TraceContext.current());
            Account account = accountRepository.findByAccountId(accountId)
                    .orElseThrow(() -> new AccountNotFoundException(accountId));
            return toResponse(account, existing.get());
        }

        // Pessimistic write lock serializes concurrent updates to the SAME
        // account. Creates the account on its first-ever transaction.
        Account account = accountRepository.findWithLockByAccountId(accountId)
                .orElseGet(() -> accountRepository.save(new Account(accountId, request.currency())));

        TransactionType type = TransactionType.valueOf(request.type());
        if (type == TransactionType.CREDIT) {
            account.applyCredit(request.amount());
        } else {
            account.applyDebit(request.amount());
        }
        accountRepository.save(account);

        AccountTransaction transaction = new AccountTransaction(
                request.eventId(), accountId, type, request.amount(), request.eventTimestamp());
        transactionRepository.save(transaction);

        log.info("Applied transaction eventId={} accountId={} newBalance={} traceId={}",
                request.eventId(), accountId, account.getBalance(), TraceContext.current());

        return toResponse(account, transaction);
    }

    public BalanceResponse getBalance(String accountId) {
        Account account = accountRepository.findByAccountId(accountId)
                .orElseThrow(() -> new AccountNotFoundException(accountId));
        return new BalanceResponse(account.getAccountId(), account.getBalance(),
                account.getCurrency(), Instant.now());
    }

    public AccountDetailResponse getAccountDetail(String accountId) {
        Account account = accountRepository.findByAccountId(accountId)
                .orElseThrow(() -> new AccountNotFoundException(accountId));

        List<AccountTransaction> recent = transactionRepository
                .findByAccountIdOrderByEventTimestampDesc(accountId, PageRequest.of(0, RECENT_TRANSACTIONS_LIMIT));

        List<AccountDetailResponse.TransactionSummary> summaries = recent.stream()
                .map(t -> new AccountDetailResponse.TransactionSummary(
                        t.getEventId(), t.getType().name(), t.getAmount(), t.getEventTimestamp()))
                .toList();

        return new AccountDetailResponse(account.getAccountId(), account.getBalance(),
                account.getCurrency(), summaries);
    }

    private TransactionResponse toResponse(Account account, AccountTransaction transaction) {
        return new TransactionResponse(
                account.getAccountId(), account.getBalance(), account.getCurrency(), Instant.now(),
                new TransactionResponse.AppliedTransaction(
                        transaction.getEventId(), transaction.getType().name(),
                        transaction.getAmount(), transaction.getEventTimestamp()));
    }
}
