package com.ddk.mcp.starter;

import com.ddk.core.exception.BusinessException;
import com.ddk.core.exception.ErrorCode;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.spec.McpSchema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.stereotype.Component;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = McpToolInvocationTest.TestApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ExtendWith(OutputCaptureExtension.class)
class McpToolInvocationTest {

    @LocalServerPort
    private int port;

    private McpSyncClient client;

    @BeforeEach
    void connect() {
        client = McpClient.sync(HttpClientStreamableHttpTransport.builder("http://localhost:" + port).build()).build();
        client.initialize();
    }

    @AfterEach
    void disconnect() {
        client.closeGracefully();
    }

    @Test
    void successfulCallReturnsTheToolResult() {
        McpSchema.CallToolResult result = call("create_order", Map.of("name", "coffee"));

        assertThat(result.isError()).isNotEqualTo(Boolean.TRUE);
        assertThat(text(result)).contains("created coffee");
    }

    @Test
    void invalidArgumentsAreRejectedBeforeTheToolRuns() {
        McpSchema.CallToolResult result = call("create_order", Map.of("name", " "));

        assertThat(result.isError()).isTrue();
        assertThat(text(result)).contains("VALIDATION_ERROR: name:");
        assertThat(OrderTools.created).doesNotContain(" ");
    }

    @Test
    void businessExceptionsCarryTheirErrorCode() {
        McpSchema.CallToolResult result = call("get_order", Map.of("id", 404));

        assertThat(result.isError()).isTrue();
        assertThat(text(result)).contains("ORDER_NOT_FOUND: order 404 does not exist");
    }

    @Test
    void unexpectedExceptionsDoNotLeakInternalDetails(CapturedOutput output) {
        McpSchema.CallToolResult result = call("sync_inventory", Map.of());

        assertThat(result.isError()).isTrue();
        assertThat(text(result)).contains("SYSTEM_ERROR").doesNotContain("jdbc:mysql");
        assertThat(output).contains("jdbc:mysql");
    }

    @Test
    void everyCallIsAudited(CapturedOutput output) {
        call("get_order", Map.of("id", 404));

        assertThat(output).contains("MCP tool [get_order] -> ORDER_NOT_FOUND in");
    }

    @Test
    void toolsDeclaredOnInterfaceImplementingBeansAreStillIntercepted() {
        McpSchema.CallToolResult result = call("ping", Map.of("target", ""));

        assertThat(text(result)).contains("VALIDATION_ERROR: target:");
    }

    private McpSchema.CallToolResult call(String tool, Map<String, Object> arguments) {
        return client.callTool(new McpSchema.CallToolRequest(tool, arguments));
    }

    private static String text(McpSchema.CallToolResult result) {
        return result.content().stream()
                .filter(McpSchema.TextContent.class::isInstance)
                .map(content -> ((McpSchema.TextContent) content).text())
                .reduce("", String::concat);
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @Import({OrderTools.class, PingTools.class})
    static class TestApplication {
    }

    enum OrderError implements ErrorCode {
        ORDER_NOT_FOUND;

        @Override
        public String getMessage() {
            return "order {0} does not exist";
        }
    }

    @Component
    static class OrderTools {

        static final java.util.List<String> created = new java.util.concurrent.CopyOnWriteArrayList<>();

        @McpTool(name = "create_order", description = "Create an order")
        public String create(@McpToolParam(description = "Order name") @NotBlank String name) {
            created.add(name);
            return "created " + name;
        }

        @McpTool(name = "get_order", description = "Get an order")
        public String get(@McpToolParam(description = "Order id") @Positive long id) {
            throw new BusinessException(OrderError.ORDER_NOT_FOUND, id);
        }

        @McpTool(name = "sync_inventory", description = "Sync inventory")
        public String sync() {
            throw new IllegalStateException("cannot connect to jdbc:mysql://10.0.0.1/prod");
        }
    }

    interface Pinger {

        String ping(@NotBlank String target);
    }

    @Component
    static class PingTools implements Pinger {

        @Override
        @McpTool(name = "ping", description = "Ping a target")
        public String ping(@McpToolParam(description = "Target host") String target) {
            return "pong " + target;
        }
    }
}
