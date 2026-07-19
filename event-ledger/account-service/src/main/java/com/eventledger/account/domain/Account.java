package com.eventledger.account.domain;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "account")
public class Account {

    @Id
    @Column(name = "account_id", length = 100)
    private String accountId;

    @Column(name = "balance", nullable = false, precision = 19, scale = 4)
    private BigDecimal balance;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    // Optimistic locking safety net to Prevent Lost Update Problem
    @Version
    private Long version;

    protected Account() {
        // JPA
    }

    public Account(String accountId, String currency) {
        this.accountId = accountId;
        this.balance = BigDecimal.ZERO;
        this.currency = currency;
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void applyCredit(BigDecimal amount) {
        this.balance = this.balance.add(amount);
        this.updatedAt = Instant.now();
    }

    public void applyDebit(BigDecimal amount) {
        this.balance = this.balance.subtract(amount);
        this.updatedAt = Instant.now();
    }

    public String getAccountId() { return accountId; }
    public BigDecimal getBalance() { return balance; }
    public String getCurrency() { return currency; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Long getVersion() { return version; }
}
