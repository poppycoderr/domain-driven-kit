package com.example.mall.order.domain.model;

/**
 * 订单状态。
 */
public enum OrderStatus {

    /** 已下单，等待支付 */
    PENDING_PAYMENT,

    /** 已取消 */
    CANCELLED
}
