package com.ddk.tracer.starter.config;

import io.micrometer.tracing.Tracer;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.actuate.autoconfigure.tracing.MicrometerTracingAutoConfiguration;
import org.springframework.boot.actuate.autoconfigure.tracing.OpenTelemetryAutoConfiguration;
import org.springframework.boot.actuate.autoconfigure.tracing.OpenTelemetryTracingAutoConfiguration;

import static org.assertj.core.api.Assertions.assertThat;

public class TracerAutoConfigurationTest {

    // Tracer 这个 Bean 不是由 MicrometerTracingAutoConfiguration 提供的，
    // 拿到它需要一条完整的自动配置链：
    //   opentelemetry.OpenTelemetryAutoConfiguration  -> Resource（服务名等资源属性）
    //   tracing.OpenTelemetryAutoConfiguration        -> OpenTelemetry SDK
    //   tracing.OpenTelemetryTracingAutoConfiguration -> SdkTracerProvider / OTel Tracer
    //   tracing.MicrometerTracingAutoConfiguration    -> 桥接成 Micrometer 的 Tracer
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withPropertyValues("spring.application.name=test-app")
            .withConfiguration(AutoConfigurations.of(
                    org.springframework.boot.actuate.autoconfigure.opentelemetry
                            .OpenTelemetryAutoConfiguration.class,
                    OpenTelemetryAutoConfiguration.class,
                    OpenTelemetryTracingAutoConfiguration.class,
                    MicrometerTracingAutoConfiguration.class,
                    TracerAutoConfiguration.class // 本 starter 的自动配置
            ));

    @Test
    void tracer_should_be_configured_by_autoconfiguration() {
        this.contextRunner.run(context -> {
            assertThat(context).hasSingleBean(TracerAutoConfiguration.class);
            assertThat(context).hasSingleBean(Tracer.class);

            // Verify service name is somewhat reflected if possible (actual check depends on Tracer impl)
            // For example, if using OpenTelemetry, you might access SdkTracerProvider and check Resource attributes.
            // This is a basic check for now.
            Tracer tracer = context.getBean(Tracer.class);
            assertThat(tracer).isNotNull();
        });
    }
}
