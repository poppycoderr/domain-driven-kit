package com.ddk.mcp.starter;

import com.ddk.concurrency.starter.Idempotent;
import com.ddk.test.containers.DdkContainers;
import com.ddk.test.containers.RedisContainer;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.spec.McpSchema;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
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
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 写工具标上 {@code @Idempotent} 时，DDK 的工具拦截在最外层：agent 重试同一个请求得到带错误码的 {@code DUPLICATE_REQUEST}，
 * 这次调用也记进审计日志。拦截的先后顺序反过来时，重复请求的异常不经过翻译，这个测试会失败。
 */
@DisplayName("MCP 写工具与防重复提交")
@Testcontainers(disabledWithoutDocker = true)
@ExtendWith(OutputCaptureExtension.class)
@SpringBootTest(classes = McpConcurrencyTest.TestApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"spring.autoconfigure.exclude=", "ddk.concurrency.key-prefix=mcp-it:"})
class McpConcurrencyTest {

    @Container
    static final RedisContainer REDIS = DdkContainers.redis();

    @LocalServerPort
    private int port;

    private McpSyncClient client;

    @DynamicPropertySource
    static void redis(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", REDIS::getPort);
    }

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
    @DisplayName("同一个 requestId 第二次调用得到带错误码的 DUPLICATE_REQUEST，工具只执行一次，审计日志记下这次拒绝")
    void aRepeatedRequestComesBackAsAnErrorCode(CapturedOutput output) {
        String requestId = UUID.randomUUID().toString();
        int before = Tools.CALLS.get();

        McpSchema.CallToolResult first = call(requestId);
        McpSchema.CallToolResult second = call(requestId);

        assertThat(first.isError()).isNotEqualTo(Boolean.TRUE);
        assertThat(text(first)).isEqualTo("cancelled");
        assertThat(second.isError()).isTrue();
        assertThat(text(second)).startsWith("DUPLICATE_REQUEST: ");
        assertThat(Tools.CALLS.get()).isEqualTo(before + 1);
        assertThat(output).contains("MCP tool [cancel] by [-] in tenant [-] -> DUPLICATE_REQUEST");
    }

    @Test
    @DisplayName("入参无效的调用不占用 requestId，修正后用同一个 requestId 重试能成功")
    void invalidArgumentsDoNotTakeTheRequestId() {
        String requestId = UUID.randomUUID().toString();

        McpSchema.CallToolResult invalid = client.callTool(new McpSchema.CallToolRequest("cancel", Map.of("requestId", requestId, "reason", " ")));
        McpSchema.CallToolResult valid = call(requestId);

        assertThat(text(invalid)).startsWith("VALIDATION_ERROR: reason:");
        assertThat(valid.isError()).isNotEqualTo(Boolean.TRUE);
    }

    private McpSchema.CallToolResult call(String requestId) {
        return client.callTool(new McpSchema.CallToolRequest("cancel", Map.of("requestId", requestId, "reason", "test")));
    }

    private static String text(McpSchema.CallToolResult result) {
        return ((McpSchema.TextContent) result.content().getFirst()).text();
    }

    static class Tools {

        static final AtomicInteger CALLS = new AtomicInteger();

        @McpTool(name = "cancel", description = "probe")
        @Idempotent(key = "#requestId")
        public String cancel(
                @McpToolParam(description = "request id") @NotBlank String requestId,
                @McpToolParam(description = "reason") @NotBlank String reason) {
            CALLS.incrementAndGet();
            return "cancelled";
        }
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    static class TestApplication {

        @Bean
        Tools tools() {
            return new Tools();
        }
    }
}
