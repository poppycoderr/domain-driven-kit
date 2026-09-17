package com.ddk.tracer.starter;

import com.ddk.tracer.starter.config.DdkTracerAutoConfiguration;
import org.springframework.boot.actuate.autoconfigure.tracing.MicrometerTracingAutoConfiguration;
import org.springframework.boot.actuate.autoconfigure.tracing.OpenTelemetryAutoConfiguration;
import org.springframework.boot.actuate.autoconfigure.tracing.OpenTelemetryTracingAutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurations;

/**
 * 拿到 Micrometer {@code Tracer} 需要的完整自动配置链：资源属性 → OTel SDK → OTel Tracer → Micrometer 桥接。
 */
public final class TracingAutoConfigurations {

    private TracingAutoConfigurations() {
    }

    public static AutoConfigurations withDdk() {
        return AutoConfigurations.of(
                org.springframework.boot.actuate.autoconfigure.opentelemetry.OpenTelemetryAutoConfiguration.class,
                OpenTelemetryAutoConfiguration.class,
                OpenTelemetryTracingAutoConfiguration.class,
                MicrometerTracingAutoConfiguration.class,
                DdkTracerAutoConfiguration.class);
    }
}
