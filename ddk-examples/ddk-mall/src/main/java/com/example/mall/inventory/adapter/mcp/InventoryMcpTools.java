package com.example.mall.inventory.adapter.mcp;

import com.example.mall.inventory.application.response.ReservationResponse;
import com.example.mall.inventory.application.response.StockResponse;
import com.example.mall.inventory.application.service.InventoryService;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 库存用例的 MCP 工具：查库存、查订单占用的库存。只开放查询，补货这类改动库存的操作不交给 agent。
 */
@Component
@RequiredArgsConstructor
public class InventoryMcpTools {

    private final InventoryService inventoryService;

    @McpTool(name = "get_stock", description = "Get the stock of a SKU: on hand, reserved by open orders, and available for sale.")
    public StockResponse stock(@McpToolParam(description = "SKU ID, for example SKU-KEYBOARD") @NotBlank String skuId) {
        return inventoryService.get(skuId);
    }

    @McpTool(name = "get_order_reservations", description = "List the stock an order holds, one entry per SKU. "
            + "Status is RESERVED (held), CONFIRMED (deducted after payment) or RELEASED (given back after cancellation).")
    public List<ReservationResponse> reservations(@McpToolParam(description = "Order ID") @Positive long orderId) {
        return inventoryService.reservationsOf(orderId);
    }
}
