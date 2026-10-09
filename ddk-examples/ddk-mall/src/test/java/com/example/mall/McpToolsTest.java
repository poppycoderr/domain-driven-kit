package com.example.mall;

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
import org.springframework.test.web.servlet.MockMvc;

import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * 一个客服 agent 通过 MCP 处理顾客的订单：查订单、查支付、查占用的库存、替顾客取消。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
class McpToolsTest {

    @LocalServerPort
    private int port;

    @Autowired
    private MockMvc mockMvc;

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

        McpSchema.CallToolResult cancelled = call("cancel_order", Map.of("customerId", 41, "orderId", orderId, "reason", "顾客来电取消"));

        assertThat(cancelled.isError()).isNotEqualTo(Boolean.TRUE);
        assertThat(text(cancelled)).contains("CANCELLED").contains("顾客来电取消");
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
        assertThat(text(call("cancel_order", Map.of("customerId", 42, "orderId", orderId, "reason", " ")))).contains("VALIDATION_ERROR: reason:");
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
