package com.eventledger.gateway.controller;

import com.eventledger.gateway.dto.request.EventSubmissionRequest;
import com.eventledger.gateway.dto.response.EventResponse;
import com.eventledger.gateway.service.EventService;
import com.eventledger.gateway.tracing.TraceContext;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/events")
public class EventController {

    private final EventService eventService;

    public EventController(EventService eventService) {
        this.eventService = eventService;
    }

    @PostMapping
    public ResponseEntity<EventResponse> submitEvent(@Valid @RequestBody EventSubmissionRequest request) {
        boolean alreadyExisted = eventService.exists(request.eventId());
        EventResponse response = eventService.submitEvent(request);

        HttpStatus status = alreadyExisted ? HttpStatus.OK : HttpStatus.CREATED;
        return ResponseEntity.status(status)
                .header("X-Trace-Id", TraceContext.current())
                .body(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<EventResponse> getEvent(@PathVariable("id") String id) {
        EventResponse response = eventService.getEvent(id);
        return ResponseEntity.ok()
                .header("X-Trace-Id", TraceContext.current())
                .body(response);
    }

    @GetMapping
    public ResponseEntity<List<EventResponse>> listEvents(@RequestParam("account") String accountId) {
        List<EventResponse> events = eventService.listByAccount(accountId);
        return ResponseEntity.ok()
                .header("X-Trace-Id", TraceContext.current())
                .body(events);
    }
}
