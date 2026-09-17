package com.example.user.adapter;

import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.spec.McpSchema;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class UserMcpToolsTest {

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
    void agentsSeeTheUseCasesAsTools() {
        assertThat(client.listTools().tools()).extracting(McpSchema.Tool::name)
                .contains("register_user", "get_user", "disable_user");
    }

    @Test
    void agentsDriveTheUserLifecycleThroughApplicationServices() {
        McpSchema.CallToolResult registered = call("register_user", register("gina01", "13900139011"));
        assertThat(registered.isError()).isNotEqualTo(Boolean.TRUE);
        String id = idOf(text(registered));

        assertThat(text(call("get_user", Map.of("id", Long.parseLong(id))))).contains("gina01").contains("139****9011");
        assertThat(text(call("disable_user", Map.of("id", Long.parseLong(id), "reason", "requested by agent")))).contains("disabled");
        assertThat(text(call("get_user", Map.of("id", Long.parseLong(id))))).contains("\"enabled\":false");
    }

    @Test
    void domainRulesComeBackAsErrorCodes() {
        McpSchema.CallToolResult duplicate = call("register_user", register("alice", "13900139012"));
        assertThat(duplicate.isError()).isTrue();
        assertThat(text(duplicate)).contains("USERNAME_TAKEN");

        assertThat(text(call("get_user", Map.of("id", 999_999)))).contains("USER_NOT_FOUND");
    }

    @Test
    void invalidArgumentsNeverReachTheApplicationService() {
        McpSchema.CallToolResult result = call("register_user", register("x", "13900139013"));

        assertThat(result.isError()).isTrue();
        assertThat(text(result)).contains("VALIDATION_ERROR: username:");
    }

    private static Map<String, Object> register(String username, String phoneNumber) {
        Map<String, Object> arguments = new HashMap<>();
        arguments.put("username", username);
        arguments.put("password", "password123");
        arguments.put("gender", 1);
        arguments.put("phoneNumber", phoneNumber);
        return arguments;
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

    private static String idOf(String json) {
        Matcher matcher = java.util.regex.Pattern.compile("\"id\":\"?(\\d+)").matcher(json);
        assertThat(matcher.find()).as("id in %s", json).isTrue();
        return matcher.group(1);
    }
}
