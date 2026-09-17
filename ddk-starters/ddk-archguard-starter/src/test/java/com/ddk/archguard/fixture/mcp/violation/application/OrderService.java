package com.ddk.archguard.fixture.mcp.violation.application;

import org.springframework.ai.mcp.annotation.McpTool;

public class OrderService {

    @McpTool(name = "cancel_order", description = "Cancel an order")
    public void cancel(long id) {
    }
}
