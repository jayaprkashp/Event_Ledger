package com.eventledger.gateway.controller;

import com.eventledger.gateway.service.EventService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(EventController.class)
class EventControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockBean
    EventService eventService;

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
                .thenThrow(new com.eventledger.gateway.exception.EventNotFoundException("missing-id"));

        mockMvc.perform(get("/events/missing-id"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("EVENT_NOT_FOUND"));
    }
}
