package com.eventledger.gateway.repository;

import com.eventledger.gateway.domain.EventRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface EventRecordRepository extends JpaRepository<EventRecord, Long> {

    // Idempotency lookup -- used both for the pre-check and for re-reading
    // after a unique-constraint violation on the late-race case.
    Optional<EventRecord> findByEventId(String eventId);

    boolean existsByEventId(String eventId);

    // Backs GET /events?account= -- ordered by event_timestamp, not insertion
    // order, so out-of-order arrivals still list chronologically.
    List<EventRecord> findByAccountIdOrderByEventTimestampAsc(String accountId);
}
