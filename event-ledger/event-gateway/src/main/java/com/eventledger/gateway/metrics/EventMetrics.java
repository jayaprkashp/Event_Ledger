package com.eventledger.gateway.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

/**
 * Custom metric requirement: request outcomes for POST /events, tagged by
 * result, exposed via Micrometer/Actuator at /actuator/prometheus and
 * /actuator/metrics/events.submitted.
 */
@Component
public class EventMetrics {

    private final Counter applied;
    private final Counter duplicate;
    private final Counter unavailable;
    private final Counter downstreamFailure;

    public EventMetrics(MeterRegistry registry) {
        this.applied = Counter.builder("events.submitted").tag("outcome", "applied").register(registry);
        this.duplicate = Counter.builder("events.submitted").tag("outcome", "duplicate").register(registry);
        this.unavailable = Counter.builder("events.submitted").tag("outcome", "account_service_unavailable").register(registry);
        this.downstreamFailure = Counter.builder("events.submitted").tag("outcome", "downstream_rejected").register(registry);
    }

    public void recordApplied() { applied.increment(); }
    public void recordDuplicate() { duplicate.increment(); }
    public void recordUnavailable() { unavailable.increment(); }
    public void recordDownstreamFailure() { downstreamFailure.increment(); }
}
