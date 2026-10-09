package com.example.mall.order.adapter.mcp;

import com.example.mall.order.application.response.OrderResponse;
import com.example.mall.order.application.service.OrderService;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

/**
 * 订单用例的 MCP 工具，面向客服场景：替顾客查订单、取消订单。
 * <p>
 * 和 {@code OrderController} 同属适配层，调用同一个应用服务。区别只在顾客身份的来源：接口从请求头取，工具由 agent 作为参数传入，
 * 应用服务里「顾客只能操作自己的订单」的规则照样生效。
 */
@Component
@RequiredArgsConstructor
public class OrderMcpTools {

    private final OrderService orderService;

    @McpTool(name = "get_order", description = "Get one order of a customer: status, amount, lines, payment deadline and cancel reason. "
            + "Status is PENDING_STOCK, PENDING_PAYMENT, PAID or CANCELLED.")
    public OrderResponse get(
            @McpToolParam(description = "Customer ID") @Positive long customerId,
            @McpToolParam(description = "Order ID") @Positive long orderId) {
        return orderService.get(customerId, orderId);
    }

    @McpTool(name = "cancel_order", description = "Cancel an order on behalf of a customer. Only orders that are not paid yet can be cancelled; "
            + "reserved stock is released and the payment is closed afterwards.")
    public OrderResponse cancel(
            @McpToolParam(description = "Customer ID") @Positive long customerId,
            @McpToolParam(description = "Order ID") @Positive long orderId,
            @McpToolParam(description = "Why the order is cancelled, up to 200 characters") @NotBlank @Size(max = 200) String reason) {
        return orderService.cancel(customerId, orderId, reason);
    }
}
