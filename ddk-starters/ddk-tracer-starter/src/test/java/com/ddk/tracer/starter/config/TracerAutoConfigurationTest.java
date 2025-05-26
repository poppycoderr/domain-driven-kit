package com.ddk.tracer.starter.config;

import io.micrometer.tracing.Tracer;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.actuate.autoconfigure.tracing.MicrometerTracingAutoConfiguration; // Added as it's likely needed for Tracer bean

import static org.assertj.core.api.Assertions.assertThat;

public class TracerAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withPropertyValues("spring.application.name=test-app")
            .withConfiguration(AutoConfigurations.of(
                    MicrometerTracingAutoConfiguration.class, // Spring Boot's tracing auto-configuration
                    TracerAutoConfiguration.class // Our auto-configuration
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
