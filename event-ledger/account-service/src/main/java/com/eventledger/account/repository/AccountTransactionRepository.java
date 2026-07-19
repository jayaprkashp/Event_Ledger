package com.eventledger.account.repository;

import com.eventledger.account.domain.AccountTransaction;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AccountTransactionRepository extends JpaRepository<AccountTransaction, Long> {

    
    Optional<AccountTransaction> findByEventId(String eventId);

    boolean existsByEventId(String eventId);

    // Backs GET /accounts/{accountId} "recentTransactions" - 50 recent transctions as configured in AccountTransactionService
    List<AccountTransaction> findByAccountIdOrderByEventTimestampDesc(String accountId, Pageable pageable);
}
