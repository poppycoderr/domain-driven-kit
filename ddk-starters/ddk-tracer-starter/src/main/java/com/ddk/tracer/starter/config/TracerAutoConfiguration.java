package com.ddk.tracer.starter.config;

import io.micrometer.tracing.Tracer;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Configuration; // Retained for potential future direct bean definitions

@AutoConfiguration
@ConditionalOnClass(Tracer.class)
public class TracerAutoConfiguration {

    // Beans can be defined here in the future if needed.
    // For example, a default SpanExporter or customizer for OpenTelemetry's SdkTracerProvider.
    // Example:
    // @Bean
    // @ConditionalOnMissingBean
    // public SpanExporter otlpHttpSpanExporter(@Value("${management.otlp.tracing.endpoint:http://localhost:4318/v1/traces}") String endpoint) {
    //     return OtlpHttpSpanExporter.builder().setEndpoint(endpoint).build();
    // }
}
