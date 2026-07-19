package com.eventledger.account.repository;

import com.eventledger.account.domain.Account;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import java.util.Optional;

public interface AccountRepository extends JpaRepository<Account, String> {

    // -- used for GET /balance and GET /accounts/{id}
    Optional<Account> findByAccountId(String accountId);

    // Pessimistic write lock, used inside the transaction-application path to
    // serialize concurrent credit/debit updates to the same account row.
    // Complements (does not replace) the @Version optimistic check on Account.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Account> findWithLockByAccountId(String accountId);
}
