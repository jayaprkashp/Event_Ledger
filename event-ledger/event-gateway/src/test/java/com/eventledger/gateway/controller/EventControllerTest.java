package com.eventledger.gateway.controller;

import com.eventledger.gateway.domain.EventStatus;
import com.eventledger.gateway.domain.TransactionType;
import com.eventledger.gateway.dto.response.EventResponse;
import com.eventledger.gateway.exception.EventNotFoundException;
import com.eventledger.gateway.service.EventService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(EventController.class)
class EventControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockBean
    EventService eventService;

    private EventResponse sampleResponse(String eventId, EventStatus status, BigDecimal balance) {
        return new EventResponse(
                eventId, "acct-1", TransactionType.CREDIT, new BigDecimal("100.00"), "USD",
                Instant.parse("2026-05-15T14:00:00Z"), null, status, balance, "USD", Instant.now());
    }

    // -----------------------------------------------------------------
    // POST /events -- validation (pre-existing)
    // -----------------------------------------------------------------

    @Test
    void negativeAmount_returns400_withFieldSpecificMessage() throws Exception {
        mockMvc.perform(post("/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"eventId\":\"e1\",\"accountId\":\"acct-1\",\"type\":\"CREDIT\",\"amount\":-5,\"currency\":\"USD\",\"eventTimestamp\":\"2026-05-15T14:00:00Z\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.error.message").value(containsString("amount")));
    }

    @Test
    void unknownType_returns400() throws Exception {
        mockMvc.perform(post("/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"eventId\":\"e2\",\"accountId\":\"acct-1\",\"type\":\"TRANSFER\",\"amount\":10,\"currency\":\"USD\",\"eventTimestamp\":\"2026-05-15T14:00:00Z\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void missingAccountQueryParam_returns400() throws Exception {
        mockMvc.perform(get("/events"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getEvent_notFound_returns404() throws Exception {
        when(eventService.getEvent("missing-id"))
                .thenThrow(new EventNotFoundException("missing-id"));

        mockMvc.perform(get("/events/missing-id"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("EVENT_NOT_FOUND"));
    }

    // -----------------------------------------------------------------
    // POST /events -- success paths (NEW)
    // -----------------------------------------------------------------

    @Test
    void newEvent_returns201Created_withAppliedStatusAndBalance() throws Exception {
        when(eventService.exists("evt-new")).thenReturn(false);
        when(eventService.submitEvent(any()))
                .thenReturn(sampleResponse("evt-new", EventStatus.APPLIED, new BigDecimal("100.00")));

        mockMvc.perform(post("/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"eventId\":\"evt-new\",\"accountId\":\"acct-1\",\"type\":\"CREDIT\",\"amount\":100.00,\"currency\":\"USD\",\"eventTimestamp\":\"2026-05-15T14:00:00Z\"}"))
                .andExpect(status().isCreated())
                .andExpect(header().exists("X-Trace-Id"))
                .andExpect(jsonPath("$.eventId").value("evt-new"))
                .andExpect(jsonPath("$.status").value("APPLIED"))
                .andExpect(jsonPath("$.balance").value(100.00));

        verify(eventService).exists("evt-new");
        verify(eventService).submitEvent(any());
    }

    @Test
    void duplicateEvent_returns200OK_notCreated() throws Exception {
        when(eventService.exists("evt-dup")).thenReturn(true);
        when(eventService.submitEvent(any()))
                .thenReturn(sampleResponse("evt-dup", EventStatus.APPLIED, new BigDecimal("50.00")));

        mockMvc.perform(post("/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"eventId\":\"evt-dup\",\"accountId\":\"acct-1\",\"type\":\"CREDIT\",\"amount\":50.00,\"currency\":\"USD\",\"eventTimestamp\":\"2026-05-15T14:00:00Z\"}"))
                .andExpect(status().isOk()) // 200, not 201
                .andExpect(jsonPath("$.eventId").value("evt-dup"));
    }

    // -----------------------------------------------------------------
    // GET /events/{id} -- success (NEW)
    // -----------------------------------------------------------------

    @Test
    void getEvent_existing_returns200() throws Exception {
        when(eventService.getEvent("evt-1"))
                .thenReturn(sampleResponse("evt-1", EventStatus.APPLIED, new BigDecimal("100.00")));

        mockMvc.perform(get("/events/evt-1"))
                .andExpect(status().isOk())
                .andExpect(header().exists("X-Trace-Id"))
                .andExpect(jsonPath("$.eventId").value("evt-1"));
    }

    // -----------------------------------------------------------------
    // GET /events?account= -- success (NEW)
    // -----------------------------------------------------------------

    @Test
    void listEvents_returns200_withArrayBody() throws Exception {
        when(eventService.listByAccount("acct-1")).thenReturn(List.of(
                sampleResponse("evt-a", EventStatus.APPLIED, new BigDecimal("10.00")),
                sampleResponse("evt-b", EventStatus.APPLIED, new BigDecimal("20.00"))
        ));

        mockMvc.perform(get("/events?account=acct-1"))
                .andExpect(status().isOk())
                .andExpect(header().exists("X-Trace-Id"))
                .andExpect(jsonPath("$[0].eventId").value("evt-a"))
                .andExpect(jsonPath("$[1].eventId").value("evt-b"));
    }
}
