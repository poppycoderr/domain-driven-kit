package com.ddk.mcp.starter.internal;

import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class McpServerDefaultsEnvironmentPostProcessorTest {

    private final McpServerDefaultsEnvironmentPostProcessor processor = new McpServerDefaultsEnvironmentPostProcessor();

    @Test
    void defaultsToStreamableHttp() {
        StandardEnvironment environment = new StandardEnvironment();

        processor.postProcessEnvironment(environment, new SpringApplication());

        assertThat(environment.getProperty("spring.ai.mcp.server.protocol")).isEqualTo("STREAMABLE");
    }

    @Test
    void explicitProtocolWins() {
        StandardEnvironment environment = new StandardEnvironment();
        environment.getPropertySources().addFirst(new MapPropertySource("application", Map.of("spring.ai.mcp.server.protocol", "STATELESS")));

        processor.postProcessEnvironment(environment, new SpringApplication());

        assertThat(environment.getProperty("spring.ai.mcp.server.protocol")).isEqualTo("STATELESS");
    }
}
