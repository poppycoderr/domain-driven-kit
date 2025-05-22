package com.ddk.archguard.starter.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import static org.assertj.core.api.Assertions.assertThat;

public class ArchGuardAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ArchGuardAutoConfiguration.class));

    @Test
    void archGuardAutoConfigurationShouldBeLoaded() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(ArchGuardAutoConfiguration.class);
        });
    }
}
