package com.ddk.mcp.starter.internal;

import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

import java.util.Map;

/**
 * 把 MCP server 的默认传输改为 streamable HTTP。
 * <p>
 * Spring AI 在未配置 {@code spring.ai.mcp.server.protocol} 时启用的是已被 MCP 规范弃用的 SSE 传输。
 * 这里以最低优先级补上默认值，应用在任何配置源里显式设置该属性都会覆盖它。
 */
public class McpServerDefaultsEnvironmentPostProcessor implements EnvironmentPostProcessor {

    static final String PROPERTY_SOURCE_NAME = "ddkMcpServerDefaults";

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        environment.getPropertySources().addLast(new MapPropertySource(PROPERTY_SOURCE_NAME, Map.of("spring.ai.mcp.server.protocol", "STREAMABLE")));
    }
}
