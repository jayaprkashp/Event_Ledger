package com.eventledger.account.domain;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(
    name = "account_transaction",
    uniqueConstraints = @UniqueConstraint(name = "uk_event_id", columnNames = "event_id"),
    indexes = @Index(name = "idx_account_event_ts", columnList = "account_id, event_timestamp")
)
public class AccountTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_id", nullable = false, updatable = false, length = 100)
    private String eventId;

    @Column(name = "account_id", nullable = false, length = 100)
    private String accountId;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 10)
    private TransactionType type;

    @Column(name = "amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Column(name = "event_timestamp", nullable = false)
    private Instant eventTimestamp;

    @Column(name = "applied_at", nullable = false, updatable = false)
    private Instant appliedAt;

    protected AccountTransaction() {
        // JPA
    }

    public AccountTransaction(String eventId, String accountId, TransactionType type,
                               BigDecimal amount, Instant eventTimestamp) {
        this.eventId = eventId;
        this.accountId = accountId;
        this.type = type;
        this.amount = amount;
        this.eventTimestamp = eventTimestamp;
        this.appliedAt = Instant.now();
    }

    public Long getId() { return id; }
    public String getEventId() { return eventId; }
    public String getAccountId() { return accountId; }
    public TransactionType getType() { return type; }
    public BigDecimal getAmount() { return amount; }
    public Instant getEventTimestamp() { return eventTimestamp; }
    public Instant getAppliedAt() { return appliedAt; }
}
