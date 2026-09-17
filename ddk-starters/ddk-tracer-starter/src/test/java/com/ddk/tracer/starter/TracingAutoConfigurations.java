package com.ddk.tracer.starter;

import com.ddk.tracer.starter.config.DdkTracerAutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.micrometer.tracing.autoconfigure.MicrometerTracingAutoConfiguration;
import org.springframework.boot.micrometer.tracing.opentelemetry.autoconfigure.OpenTelemetryTracingAutoConfiguration;
import org.springframework.boot.opentelemetry.autoconfigure.OpenTelemetrySdkAutoConfiguration;

/**
 * 拿到 Micrometer {@code Tracer} 需要的完整自动配置链：OTel SDK → OTel Tracer → Micrometer 桥接。
 */
public final class TracingAutoConfigurations {

    private TracingAutoConfigurations() {
    }

    public static AutoConfigurations withDdk() {
        return AutoConfigurations.of(
                OpenTelemetrySdkAutoConfiguration.class,
                OpenTelemetryTracingAutoConfiguration.class,
                MicrometerTracingAutoConfiguration.class,
                DdkTracerAutoConfiguration.class);
    }
}
