package com.example.mall.order.adapter.mcp;

import com.ddk.concurrency.starter.Idempotent;
import com.example.mall.order.application.query.OrderSearchQuery;
import com.example.mall.order.application.response.OrderResponse;
import com.example.mall.order.application.response.OrderSummaryResponse;
import com.example.mall.order.application.service.OrderSearchService;
import com.example.mall.order.application.service.OrderService;
import com.example.mall.platform.CustomerAccess;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 订单用例的 MCP 工具，面向客服场景：替顾客搜订单、查订单、取消订单。
 * <p>
 * 和 {@code OrderController} 同属适配层，调用同一个应用服务。区别只在顾客身份的来源：接口取自当前请求的操作者，工具由 agent 作为参数传入。
 * 参数不是可信的身份，所以先经 {@link CustomerAccess} 检查（secure profile 下只能是登录的顾客本人或客服），
 * 应用服务里「顾客只能操作自己的订单」的规则照样生效。
 * <p>
 * 写工具带一个 agent 生成的 {@code requestId} 并标上 {@code @Idempotent}：agent 在超时之后重试，同一个请求不会执行两次。
 * DDK 的工具拦截在它外面，重复的请求以 {@code DUPLICATE_REQUEST} 返回给 agent，审计日志记下操作者和租户。
 */
@Component
@RequiredArgsConstructor
public class OrderMcpTools {

    private final OrderService orderService;

    private final OrderSearchService orderSearchService;

    private final CustomerAccess customerAccess;

    @McpTool(name = "get_order", description = "Get one order of a customer: status, amount, lines, payment deadline and cancel reason. "
            + "Status is PENDING_STOCK, PENDING_PAYMENT, PAID or CANCELLED.")
    public OrderResponse get(
            @McpToolParam(description = "Customer ID") @Positive long customerId,
            @McpToolParam(description = "Order ID") @Positive long orderId) {
        customerAccess.actFor(customerId);
        return orderService.get(customerId, orderId);
    }

    @McpTool(name = "search_orders", description = "Find a customer's orders by product name and status, newest first, up to 20. "
            + "Results come from a search read model that lags the order by a moment.")
    public List<OrderSummaryResponse> search(
            @McpToolParam(description = "Customer ID") @Positive long customerId,
            @McpToolParam(description = "Part of a product name; omit to list all", required = false) @Nullable String keyword,
            @McpToolParam(description = "PENDING_STOCK, PENDING_PAYMENT, PAID or CANCELLED; omit for any", required = false)
            @Nullable String status) {
        customerAccess.actFor(customerId);
        OrderSearchQuery query = new OrderSearchQuery();
        query.setKeyword(keyword);
        query.setStatus(status);
        query.setPageSize(20L);
        return orderSearchService.search(customerId, query).getRecords();
    }

    @McpTool(name = "cancel_order", description = "Cancel an order on behalf of a customer. Only orders that are not paid yet can be cancelled; "
            + "reserved stock is released and the payment is closed afterwards. "
            + "Generate a new requestId for each cancellation and send the same one again when you retry it. "
            + "DUPLICATE_REQUEST means that requestId was already used: check the order with get_order instead of retrying.")
    @Idempotent(key = "#requestId")
    public OrderResponse cancel(
            @McpToolParam(description = "A unique ID for this cancellation, such as a UUID; the same ID on a retry")
            @NotBlank @Size(max = 64) String requestId,
            @McpToolParam(description = "Customer ID") @Positive long customerId,
            @McpToolParam(description = "Order ID") @Positive long orderId,
            @McpToolParam(description = "Why the order is cancelled, up to 200 characters") @NotBlank @Size(max = 200) String reason) {
        customerAccess.actFor(customerId);
        return orderService.cancel(customerId, orderId, reason);
    }
}
