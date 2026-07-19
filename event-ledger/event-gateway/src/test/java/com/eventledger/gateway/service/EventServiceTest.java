package com.eventledger.gateway.service;

import com.eventledger.gateway.client.AccountServiceClient;
import com.eventledger.gateway.domain.EventRecord;
import com.eventledger.gateway.domain.EventStatus;
import com.eventledger.gateway.domain.TransactionType;
import com.eventledger.gateway.dto.internal.ApplyTransactionResponse;
import com.eventledger.gateway.dto.request.EventSubmissionRequest;
import com.eventledger.gateway.dto.response.EventResponse;
import com.eventledger.gateway.exception.AccountServiceUnavailableException;
import com.eventledger.gateway.metrics.EventMetrics;
import com.eventledger.gateway.repository.EventRecordRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EventServiceTest {

    @Mock EventRecordRepository repository;
    @Mock AccountServiceClient accountServiceClient;
    @Mock EventMetrics metrics;

    EventService service;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        service = new EventService(repository, accountServiceClient, objectMapper, metrics);
    }

    private EventRecord sampleEventRecord(String eventId, EventStatus status) {
        EventRecord record = new EventRecord(eventId, "acct-1", TransactionType.CREDIT,
                new BigDecimal("100.00"), "USD", Instant.parse("2026-05-15T14:00:00Z"), null);
        if (status == EventStatus.APPLIED) {
            record.markApplied();
        }
        return record;
    }

    private EventSubmissionRequest sampleRequest(String eventId) {
        return new EventSubmissionRequest(eventId, "acct-1", "CREDIT", new BigDecimal("100.00"),
                "USD", Instant.parse("2026-05-15T14:00:00Z"), null);
    }

    @Test
    void duplicateSubmission_doesNotCallAccountService_returnsOriginalRecord() {
        EventRecord existing = sampleEventRecord("evt-001", EventStatus.APPLIED);
        when(repository.findByEventId("evt-001")).thenReturn(Optional.of(existing));

        EventResponse response = service.submitEvent(sampleRequest("evt-001"));

        assertThat(response.status()).isEqualTo(EventStatus.APPLIED);
        verifyNoInteractions(accountServiceClient);
        verify(metrics).recordDuplicate();
    }

    @Test
    void newEvent_accountServiceUnavailable_leavesStatusPending_propagatesException() {
        when(repository.findByEventId("evt-002")).thenReturn(Optional.empty());
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(accountServiceClient.applyTransaction(any(), any(), any()))
                .thenThrow(new AccountServiceUnavailableException("down", null));

        assertThatThrownBy(() -> service.submitEvent(sampleRequest("evt-002")))
                .isInstanceOf(AccountServiceUnavailableException.class);

        verify(repository, never()).save(argThat(r -> r.getStatus() == EventStatus.APPLIED));
        verify(metrics).recordUnavailable();
    }

    @Test
    void newEvent_accountServiceSucceeds_marksApplied_includesBalance() {
        when(repository.findByEventId("evt-003")).thenReturn(Optional.empty());
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(accountServiceClient.applyTransaction(any(), any(), any()))
                .thenReturn(new ApplyTransactionResponse("acct-1", new BigDecimal("150.00"), "USD"));

        EventResponse response = service.submitEvent(sampleRequest("evt-003"));

        assertThat(response.status()).isEqualTo(EventStatus.APPLIED);
        assertThat(response.balance()).isEqualByComparingTo("150.00");
        verify(metrics).recordApplied();
    }

    @Test
    void listByAccount_returnsRepositoryOrderUnchanged() {
        EventRecord earlier = sampleEventRecord("evt-a", EventStatus.APPLIED);
        EventRecord later = sampleEventRecord("evt-b", EventStatus.APPLIED);
        when(repository.findByAccountIdOrderByEventTimestampAsc("acct-1"))
                .thenReturn(List.of(earlier, later));

        List<EventResponse> results = service.listByAccount("acct-1");

        assertThat(results).extracting(EventResponse::eventId).containsExactly("evt-a", "evt-b");
    }
}
