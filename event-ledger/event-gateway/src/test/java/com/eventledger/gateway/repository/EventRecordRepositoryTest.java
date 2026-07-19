package com.eventledger.gateway.repository;

import com.eventledger.gateway.domain.EventRecord;
import com.eventledger.gateway.domain.TransactionType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
class EventRecordRepositoryTest {

    @Autowired
    EventRecordRepository repository;

    private EventRecord recordWithTimestamp(String eventId, String accountId, Instant eventTimestamp) {
        return new EventRecord(eventId, accountId, TransactionType.CREDIT,
                new BigDecimal("10.00"), "USD", eventTimestamp, null);
    }

    @Test
    void findByAccountId_returnsResultsOrderedByEventTimestamp_regardlessOfInsertOrder() {
        repository.save(recordWithTimestamp("evt-b", "acct-1", Instant.parse("2026-05-15T14:05:00Z")));
        repository.save(recordWithTimestamp("evt-a", "acct-1", Instant.parse("2026-05-15T14:00:00Z")));

        List<EventRecord> results = repository.findByAccountIdOrderByEventTimestampAsc("acct-1");

        assertThat(results).extracting(EventRecord::getEventId).containsExactly("evt-a", "evt-b");
    }

    @Test
    void duplicateEventId_violatesUniqueConstraint() {
        repository.save(recordWithTimestamp("evt-dup", "acct-1", Instant.now()));
        repository.flush();

        assertThatThrownBy(() -> {
            repository.save(recordWithTimestamp("evt-dup", "acct-1", Instant.now()));
            repository.flush();
        }).isInstanceOf(DataIntegrityViolationException.class);
    }
}
