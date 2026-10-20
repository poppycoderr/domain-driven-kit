package com.ddk.mcp.starter;

import com.ddk.core.context.Operator;
import com.ddk.core.context.OperatorContext;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.spec.McpSchema;
import jakarta.servlet.Filter;
import jakarta.servlet.http.HttpServletRequest;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * MCP 工具和 Web 入口共用 {@link OperatorContext}：默认的同步 server 加 streamable HTTP 传输在 HTTP 请求线程上执行工具，
 * 过滤器设置的操作者（多租户应用里连同租户）在工具里可见，请求结束后清除。换成异步 server 时不成立，这个测试会失败。
 */
@DisplayName("MCP 工具里的操作者上下文")
@SpringBootTest(classes = McpOperatorContextTest.TestApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class McpOperatorContextTest {

    @LocalServerPort
    private int port;

    @Test
    @DisplayName("请求过滤器设置的操作者在工具里可见；没有操作者的请求看不到上一个请求的操作者")
    void toolsSeeTheOperatorOfTheirRequest() {
        assertThat(whoami("alice")).isEqualTo("alice/tenant-1");
        assertThat(whoami(null)).isEqualTo("none");
        assertThat(whoami("bob")).isEqualTo("bob/tenant-1");
    }

    private String whoami(@Nullable String operator) {
        McpSyncClient client = McpClient.sync(HttpClientStreamableHttpTransport.builder("http://localhost:" + port)
                .httpRequestCustomizer((builder, method, uri, body, context) -> {
                    if (operator != null) {
                        builder.header("X-Operator", operator);
                    }
                })
                .build()).build();
        try {
            client.initialize();
            McpSchema.CallToolResult result = client.callTool(new McpSchema.CallToolRequest("whoami", Map.of()));
            return ((McpSchema.TextContent) result.content().getFirst()).text();
        } finally {
            client.closeGracefully();
        }
    }

    @Component
    static class Tools {

        @McpTool(name = "whoami", description = "probe")
        public String whoami() {
            return OperatorContext.current().map(operator -> operator.id() + "/" + operator.tenantId()).orElse("none");
        }
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    static class TestApplication {

        @Bean
        Tools tools() {
            return new Tools();
        }

        @Bean
        Filter operatorFilter() {
            return (request, response, chain) -> {
                String id = ((HttpServletRequest) request).getHeader("X-Operator");
                if (id == null) {
                    chain.doFilter(request, response);
                    return;
                }
                OperatorContext.set(Operator.of(id, "tenant-1"));
                try {
                    chain.doFilter(request, response);
                } finally {
                    OperatorContext.clear();
                }
            };
        }
    }
}
