package com.ddk.archguard.fixture.mcp.valid.adapter.mcp;

import org.springframework.ai.mcp.annotation.McpTool;

public class OrderTools {

    @McpTool(name = "get_order", description = "Get an order")
    public String get(long id) {
        return "order " + id;
    }
}
