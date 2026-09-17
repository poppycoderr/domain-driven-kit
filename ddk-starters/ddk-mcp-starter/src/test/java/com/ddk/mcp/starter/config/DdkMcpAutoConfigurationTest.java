package com.ddk.mcp.starter.config;

import com.ddk.mcp.starter.internal.McpToolAdvisingPostProcessor;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class DdkMcpAutoConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(DdkMcpAutoConfiguration.class));

    @Test
    void interceptsMcpToolsByDefault() {
        runner.run(context -> assertThat(context).hasSingleBean(McpToolAdvisingPostProcessor.class).hasSingleBean(DdkMcpProperties.class));
    }

    @Test
    void canBeSwitchedOff() {
        runner.withPropertyValues("ddk.mcp.enabled=false")
                .run(context -> assertThat(context).doesNotHaveBean(McpToolAdvisingPostProcessor.class));
    }
}
