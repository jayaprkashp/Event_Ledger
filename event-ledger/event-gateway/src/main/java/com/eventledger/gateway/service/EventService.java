package com.eventledger.gateway.service;

import com.eventledger.gateway.client.AccountServiceClient;
import com.eventledger.gateway.domain.EventRecord;
import com.eventledger.gateway.domain.TransactionType;
import com.eventledger.gateway.dto.internal.ApplyTransactionRequest;
import com.eventledger.gateway.dto.internal.ApplyTransactionResponse;
import com.eventledger.gateway.dto.request.EventSubmissionRequest;
import com.eventledger.gateway.dto.response.EventResponse;
import com.eventledger.gateway.exception.AccountServiceUnavailableException;
import com.eventledger.gateway.exception.DownstreamRejectionException;
import com.eventledger.gateway.exception.EventNotFoundException;
import com.eventledger.gateway.metrics.EventMetrics;
import com.eventledger.gateway.repository.EventRecordRepository;
import com.eventledger.gateway.tracing.TraceContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Service
public class EventService {

    private static final Logger log = LoggerFactory.getLogger(EventService.class);

    private final EventRecordRepository repository;
    private final AccountServiceClient accountServiceClient;
    private final ObjectMapper objectMapper;
    private final EventMetrics metrics;

    public EventService(EventRecordRepository repository,
                         AccountServiceClient accountServiceClient,
                         ObjectMapper objectMapper,
                         EventMetrics metrics) {
        this.repository = repository;
        this.accountServiceClient = accountServiceClient;
        this.objectMapper = objectMapper;
        this.metrics = metrics;
    }

    public boolean exists(String eventId) {
        return repository.existsByEventId(eventId);
    }

    /**
     * Orchestrates: idempotency check -> persist PENDING -> call Account Service
     * -> update status. The DB insert and the downstream call are deliberately
     * NOT wrapped in a single @Transactional boundary -- holding a DB
     * connection open across a network round-trip to another process risks
     * exhausting the connection pool if that dependency is slow.
     */
    public EventResponse submitEvent(EventSubmissionRequest request) {
        var existing = repository.findByEventId(request.eventId());
        if (existing.isPresent()) {
            log.info("Duplicate event submission eventId={} traceId={}",
                    request.eventId(), TraceContext.current());
            metrics.recordDuplicate();
            return toResponse(existing.get(), null, null);
        }

        EventRecord record = persistPending(request);

        try {
            ApplyTransactionResponse downstream = accountServiceClient.applyTransaction(
                    request.accountId(),
                    new ApplyTransactionRequest(
                            request.eventId(),
                            TransactionType.valueOf(request.type()),
                            request.amount(),
                            request.currency(),
                            request.eventTimestamp()
                    ),
                    TraceContext.current()
            );
            record.markApplied();
            repository.save(record);
            metrics.recordApplied();
            log.info("An event triggered with eventId={} traceId={}",
                    request.eventId(), TraceContext.current());
            return toResponse(record, downstream.balance(), downstream.currency());

        } catch (AccountServiceUnavailableException ex) {
            // Record stays PENDING -- not a terminal failure, may succeed on a
            // future retry/resubmission. Re-thrown for the controller to map to 503.
            log.warn("Account service unavailable, event left PENDING eventId={} traceId={}",
                    request.eventId(), TraceContext.current());
            metrics.recordUnavailable();
            throw ex;

        } catch (DownstreamRejectionException ex) {
            // Account service explicitly rejected it -- this IS terminal.
            record.markFailedDownstream(ex.getMessage());
            repository.save(record);
            metrics.recordDownstreamFailure();
            throw ex;
        }
    }

    public EventResponse getEvent(String eventId) {
        EventRecord record = repository.findByEventId(eventId)
                .orElseThrow(() -> new EventNotFoundException(eventId));
        return toResponse(record, null, null);
    }

    public List<EventResponse> listByAccount(String accountId) {
        return repository.findByAccountIdOrderByEventTimestampAsc(accountId)
                .stream()
                .map(r -> toResponse(r, null, null))
                .toList();
    }

    @Transactional
    protected EventRecord persistPending(EventSubmissionRequest request) {
        String metadataJson = request.metadata() != null
                ? writeMetadata(request.metadata())
                : null;
        EventRecord record = new EventRecord(
                request.eventId(), request.accountId(),
                TransactionType.valueOf(request.type()), request.amount(),
                request.currency(), request.eventTimestamp(), metadataJson
        );
        return repository.save(record);
    }

    private String writeMetadata(Object metadata) {
        try {
            return objectMapper.writeValueAsString(metadata);
        } catch (Exception e) {
            log.warn("Failed to serialize metadata, storing as null: {}", e.getMessage());
            return null;
        }
    }

    private EventResponse toResponse(EventRecord r, BigDecimal balance, String balanceCurrency) {
        return new EventResponse(
                r.getEventId(), r.getAccountId(), r.getType(), r.getAmount(), r.getCurrency(),
                r.getEventTimestamp(), readMetadata(r.getMetadataJson()), r.getStatus(),
                balance, balanceCurrency, r.getReceivedAt()
        );
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> readMetadata(String json) {
        if (json == null) return null;
        try {
            return objectMapper.readValue(json, Map.class);
        } catch (Exception e) {
            return null;
        }
    }
}
