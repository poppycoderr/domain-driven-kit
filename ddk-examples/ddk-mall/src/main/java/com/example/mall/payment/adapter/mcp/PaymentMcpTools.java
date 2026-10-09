package com.example.mall.payment.adapter.mcp;

import com.example.mall.payment.application.response.PaymentResponse;
import com.example.mall.payment.application.service.PaymentService;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

/**
 * 支付用例的 MCP 工具：查订单的支付情况。发起支付要由顾客本人操作，不开放成工具。
 */
@Component
@RequiredArgsConstructor
public class PaymentMcpTools {

    private final PaymentService paymentService;

    @McpTool(name = "get_payment", description = "Get the payment of a customer's order: amount, deadline, channel trade number. "
            + "Status is PENDING, PAID, CLOSED (never paid) or REFUNDED (paid, then the order was cancelled).")
    public PaymentResponse get(
            @McpToolParam(description = "Customer ID") @Positive long customerId,
            @McpToolParam(description = "Order ID") @Positive long orderId) {
        return paymentService.get(customerId, orderId);
    }
}
