package com.example.mall.platform;

import com.jayway.jsonpath.JsonPath;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.spec.McpSchema;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * secure profile：身份来自认证结果而不是请求头，管理操作只给客服，MCP 工具的 customerId 参数不能冒充别的顾客。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("secure")
class SecureProfileTest {

    private static final String PASSWORD = "example_password";

    @Autowired
    private MockMvc mockMvc;

    @LocalServerPort
    private int port;

    @Test
    void anonymousRequestsAreRejectedButHealthAndDocsArePublic() throws Exception {
        mockMvc.perform(get("/orders/1").header(CustomerIdentity.HEADER, 7)).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/orders/1").with(httpBasic("7", "wrong"))).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/actuator/health")).andExpect(status().isOk());
        mockMvc.perform(get("/v3/api-docs/order")).andExpect(status().isOk());
    }

    @Test
    void theIdentityComesFromTheLoginNotFromTheHeader() throws Exception {
        long orderId = placeOrder("51");

        mockMvc.perform(get("/orders/{id}", orderId).with(httpBasic("51", PASSWORD)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.id").value(String.valueOf(orderId)));
        mockMvc.perform(get("/orders/{id}", orderId).with(httpBasic("52", PASSWORD)).header(CustomerIdentity.HEADER, 51))
                .andExpect(jsonPath("$.code").value("ORDER_NOT_FOUND"));
        mockMvc.perform(post("/orders/{id}/cancel", orderId).with(httpBasic("52", PASSWORD)).header(CustomerIdentity.HEADER, 51)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"不是我的订单\"}"))
                .andExpect(jsonPath("$.code").value("ORDER_NOT_FOUND"));
        mockMvc.perform(get("/orders/{id}", orderId).with(httpBasic("support", PASSWORD)))
                .andExpect(status().isForbidden());
    }

    @Test
    void administrativeOperationsNeedTheSupportRole() throws Exception {
        mockMvc.perform(post("/inventory/SKU-MOUSE/restock").with(httpBasic("51", PASSWORD))
                .contentType(MediaType.APPLICATION_JSON).content("{\"quantity\":1}")).andExpect(status().isForbidden());
        mockMvc.perform(post("/orders/search/rebuild").with(httpBasic("51", PASSWORD))).andExpect(status().isForbidden());
        mockMvc.perform(get("/actuator/metrics").with(httpBasic("51", PASSWORD))).andExpect(status().isForbidden());
        mockMvc.perform(get("/inventory/SKU-MOUSE").with(httpBasic("51", PASSWORD))).andExpect(status().isOk());

        mockMvc.perform(post("/inventory/SKU-MOUSE/restock").with(httpBasic("support", PASSWORD))
                .contentType(MediaType.APPLICATION_JSON).content("{\"quantity\":1}")).andExpect(status().isOk());
        mockMvc.perform(post("/orders/search/rebuild").with(httpBasic("support", PASSWORD))).andExpect(status().isOk());
    }

    @Test
    void mcpToolsCannotActForAnotherCustomer() throws Exception {
        long orderId = placeOrder("53");
        await().atMost(Duration.ofSeconds(10)).until(() -> text(call("53", "get_order", Map.of("customerId", 53, "orderId", orderId)))
                .contains("PENDING_PAYMENT"));

        assertThat(call(null, "get_order", Map.of("customerId", 53, "orderId", orderId))).as("anonymous").isNull();
        McpSchema.CallToolResult impersonation = call("54", "get_order", Map.of("customerId", 53, "orderId", orderId));
        assertThat(impersonation.isError()).isTrue();
        assertThat(text(impersonation)).contains("ACTING_FOR_OTHER_CUSTOMER");
        assertThat(text(call("54", "cancel_order", Map.of("customerId", 53, "orderId", orderId, "reason", "冒充"))))
                .contains("ACTING_FOR_OTHER_CUSTOMER");
        assertThat(text(call("53", "get_order_reservations", Map.of("orderId", orderId)))).contains("SUPPORT_REQUIRED");

        assertThat(text(call("support", "get_order", Map.of("customerId", 53, "orderId", orderId)))).contains("PENDING_PAYMENT");
        assertThat(text(call("support", "get_order_reservations", Map.of("orderId", orderId)))).contains("RESERVED");
    }

    private long placeOrder(String customer) throws Exception {
        String body = mockMvc.perform(post("/orders").with(httpBasic(customer, PASSWORD)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"lines\":[{\"skuId\":\"SKU-MOUSE\",\"quantity\":1}]}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return Long.parseLong(JsonPath.read(body, "$.data.id"));
    }

    /**
     * 以 {@code user} 的身份调用工具；{@code user} 为 null 时不带凭证，连接被拒绝时返回 null。
     */
    private McpSchema.CallToolResult call(String user, String tool, Map<String, Object> arguments) {
        HttpClientStreamableHttpTransport.Builder transport = HttpClientStreamableHttpTransport.builder("http://localhost:" + port);
        if (user != null) {
            String credentials = Base64.getEncoder().encodeToString((user + ":" + PASSWORD).getBytes(StandardCharsets.UTF_8));
            transport.httpRequestCustomizer((builder, method, uri, body, context) -> builder.header("Authorization", "Basic " + credentials));
        }
        McpSyncClient client = McpClient.sync(transport.build()).requestTimeout(Duration.ofSeconds(5)).build();
        try {
            client.initialize();
            return client.callTool(new McpSchema.CallToolRequest(tool, arguments));
        } catch (RuntimeException e) {
            return null;
        } finally {
            client.closeGracefully();
        }
    }

    private static String text(McpSchema.CallToolResult result) {
        return result.content().stream()
                .filter(McpSchema.TextContent.class::isInstance)
                .map(content -> ((McpSchema.TextContent) content).text())
                .findFirst().orElse("");
    }
}
