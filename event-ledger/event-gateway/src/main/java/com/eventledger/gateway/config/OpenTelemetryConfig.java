package com.eventledger.gateway.config;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.exporter.logging.LoggingSpanExporter;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Exports spans to the log stream rather than a remote collector -- keeps this
 * self-contained for the exercise (no external Jaeger/Zipkin dependency
 * required) while still producing real OTel spans. Swapping the exporter to
 * OTLP is a one-line change if trace visualization via a collector is added later.
 */
@Configuration
public class OpenTelemetryConfig {

    @Bean
    public OpenTelemetry openTelemetry() {
        SdkTracerProvider tracerProvider = SdkTracerProvider.builder()
                .addSpanProcessor(SimpleSpanProcessor.create(LoggingSpanExporter.create()))
                .setResource(Resource.getDefault().merge(
                        Resource.builder().put("service.name", "event-gateway").build()))
                .build();
        return OpenTelemetrySdk.builder().setTracerProvider(tracerProvider).build();
    }
}
