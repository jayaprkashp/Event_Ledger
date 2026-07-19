package com.eventledger.gateway.domain;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(
    name = "event_record",
    uniqueConstraints = @UniqueConstraint(name = "uk_event_id", columnNames = "event_id"),
    indexes = @Index(name = "idx_account_event_ts", columnList = "account_id, event_timestamp")
)
public class EventRecord {

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

    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    @Column(name = "event_timestamp", nullable = false)
    private Instant eventTimestamp;

    // Stored as a serialized JSON string; (de)serialized in the service layer.
    // H2 has no first-class JSON column type across all supported versions,
    // so this stays a plain text column rather than reaching for a converter
    // that adds complexity with no behavioral payoff for this exercise.
    @Column(name = "metadata", length = 2000)
    private String metadataJson;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private EventStatus status;

    @Column(name = "failure_reason", length = 500)
    private String failureReason;

    @Column(name = "received_at", nullable = false, updatable = false)
    private Instant receivedAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected EventRecord() {
        // JPA requires a no-arg constructor
    }

    public EventRecord(String eventId, String accountId, TransactionType type, BigDecimal amount,
                        String currency, Instant eventTimestamp, String metadataJson) {
        this.eventId = eventId;
        this.accountId = accountId;
        this.type = type;
        this.amount = amount;
        this.currency = currency;
        this.eventTimestamp = eventTimestamp;
        this.metadataJson = metadataJson;
        this.status = EventStatus.PENDING;
        Instant now = Instant.now();
        this.receivedAt = now;
        this.updatedAt = now;
    }

    public void markApplied() {
        this.status = EventStatus.APPLIED;
        this.updatedAt = Instant.now();
    }

    public void markFailedDownstream(String reason) {
        this.status = EventStatus.FAILED_DOWNSTREAM;
        this.failureReason = reason;
        this.updatedAt = Instant.now();
    }

    public Long getId() { return id; }
    public String getEventId() { return eventId; }
    public String getAccountId() { return accountId; }
    public TransactionType getType() { return type; }
    public BigDecimal getAmount() { return amount; }
    public String getCurrency() { return currency; }
    public Instant getEventTimestamp() { return eventTimestamp; }
    public String getMetadataJson() { return metadataJson; }
    public EventStatus getStatus() { return status; }
    public String getFailureReason() { return failureReason; }
    public Instant getReceivedAt() { return receivedAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
