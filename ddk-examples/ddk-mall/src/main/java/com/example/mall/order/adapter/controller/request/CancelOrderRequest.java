package com.example.mall.order.adapter.controller.request;

import jakarta.validation.constraints.Size;

/**
 * 取消订单请求。
 */
public record CancelOrderRequest(
        @Size(max = 200)
        String reason
) {
}
