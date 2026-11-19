package com.example.mall;

import com.ddk.test.containers.DdkContainers;
import com.ddk.test.containers.RedisContainer;
import com.jayway.jsonpath.JsonPath;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.spec.McpSchema;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * 一个客服 agent 通过 MCP 处理顾客的订单：查订单、查支付、查占用的库存、替顾客取消；重试同一个取消请求不会再执行一次。
 * <p>
 * {@code cancel_order} 的防重复提交登记在 Redis 里，默认 profile 关掉了它，所以这里像 compose profile 一样连上 Redis、打开 concurrency starter。
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"spring.autoconfigure.exclude=", "ddk.concurrency.enabled=true", "ddk.concurrency.key-prefix=mall-mcp-test:"})
@AutoConfigureMockMvc
class McpToolsTest {

    @Container
    static final RedisContainer REDIS = DdkContainers.redis();

    @LocalServerPort
    private int port;

    @Autowired
    private MockMvc mockMvc;

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
    void theThreeContextsExposeTheirUseCasesAsTools() {
        assertThat(client.listTools().tools()).extracting(McpSchema.Tool::name)
                .containsExactlyInAnyOrder("get_order", "search_orders", "cancel_order", "get_stock", "get_order_reservations", "get_payment");
    }

    @Test
    void anAgentLooksIntoAnOrderAndCancelsItForTheCustomer() throws Exception {
        long orderId = placeOrder(41L);
        await().atMost(Duration.ofSeconds(10)).until(() -> text(call("get_order", Map.of("customerId", 41, "orderId", orderId))).contains("PENDING_PAYMENT"));
        await().atMost(Duration.ofSeconds(10)).until(() -> text(call("get_payment", Map.of("customerId", 41, "orderId", orderId))).contains("PENDING"));
        assertThat(text(call("get_order_reservations", Map.of("orderId", orderId)))).contains("SKU-MOUSE").contains("RESERVED");
        await().atMost(Duration.ofSeconds(10)).until(() -> text(call("search_orders", Map.of("customerId", 41, "keyword", "鼠标")))
                .contains(String.valueOf(orderId)));
        assertThat(text(call("get_stock", Map.of("skuId", "SKU-MOUSE")))).contains("\"available\"");

        Map<String, Object> cancel = Map.of("requestId", "cancel-" + orderId, "customerId", 41, "orderId", orderId, "reason", "顾客来电取消");
        McpSchema.CallToolResult cancelled = call("cancel_order", cancel);
        McpSchema.CallToolResult retried = call("cancel_order", cancel);

        assertThat(cancelled.isError()).isNotEqualTo(Boolean.TRUE);
        assertThat(text(cancelled)).contains("CANCELLED").contains("顾客来电取消");
        assertThat(retried.isError()).isTrue();
        assertThat(text(retried)).startsWith("DUPLICATE_REQUEST: ");
        await().atMost(Duration.ofSeconds(10)).until(() -> text(call("get_payment", Map.of("customerId", 41, "orderId", orderId))).contains("CLOSED"));
        await().atMost(Duration.ofSeconds(10)).until(() -> text(call("get_order_reservations", Map.of("orderId", orderId))).contains("RELEASED"));
    }

    @Test
    void domainRulesAndInvalidArgumentsComeBackAsErrorCodes() throws Exception {
        long orderId = placeOrder(42L);

        McpSchema.CallToolResult someoneElse = call("get_order", Map.of("customerId", 43, "orderId", orderId));
        assertThat(someoneElse.isError()).isTrue();
        assertThat(text(someoneElse)).contains("ORDER_NOT_FOUND");
        assertThat(text(call("get_stock", Map.of("skuId", "SKU-GONE")))).contains("STOCK_NOT_FOUND");
        assertThat(text(call("cancel_order", Map.of("requestId", "cancel-" + orderId, "customerId", 42, "orderId", orderId, "reason", " ")))).contains("VALIDATION_ERROR: reason:");
        assertThat(text(call("get_payment", Map.of("customerId", 0, "orderId", orderId)))).contains("VALIDATION_ERROR: customerId:");
    }

    private long placeOrder(long customerId) throws Exception {
        String body = mockMvc.perform(post("/orders").header("X-Customer-Id", customerId).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"lines\":[{\"skuId\":\"SKU-MOUSE\",\"quantity\":1}]}"))
                .andReturn().getResponse().getContentAsString();
        return Long.parseLong(JsonPath.read(body, "$.data.id"));
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
}
