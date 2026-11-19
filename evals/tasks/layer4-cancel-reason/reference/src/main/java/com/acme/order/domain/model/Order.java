package com.acme.order.domain.model;

import com.acme.order.domain.error.OrderError;
import com.acme.order.domain.event.OrderPlacedEvent;
import com.ddk.core.domain.AggregateRoot;
import com.ddk.core.exception.BusinessException;

/**
 * 订单聚合根。
 */
public class Order extends AggregateRoot<OrderId> {

    private static final int MAX_CANCEL_REASON_LENGTH = 200;

    private long customerId;
    private long amount;
    private OrderStatus status;
    private String cancelReason;

    private Order() {
    }

    public static Order place(OrderId id, long customerId, long amount) {
        if (amount <= 0) {
            throw new BusinessException(OrderError.INVALID_AMOUNT, amount);
        }
        Order order = new Order();
        order.assignId(id);
        order.customerId = customerId;
        order.amount = amount;
        order.status = OrderStatus.PLACED;
        order.registerEvent(new OrderPlacedEvent(id, customerId, amount));
        return order;
    }

    public static Order restore(OrderId id, long customerId, long amount, OrderStatus status, String cancelReason, Long version) {
        Order order = new Order();
        order.assignId(id);
        order.customerId = customerId;
        order.amount = amount;
        order.status = status;
        order.cancelReason = cancelReason;
        order.assignVersion(version);
        return order;
    }

    public void cancel(String reason) {
        if (status != OrderStatus.PLACED) {
            throw new BusinessException(OrderError.ORDER_NOT_CANCELLABLE, status);
        }
        String trimmed = reason == null || reason.isBlank() ? null : reason.strip();
        if (trimmed != null && trimmed.length() > MAX_CANCEL_REASON_LENGTH) {
            throw new BusinessException(OrderError.CANCEL_REASON_TOO_LONG, MAX_CANCEL_REASON_LENGTH);
        }
        status = OrderStatus.CANCELLED;
        cancelReason = trimmed;
    }

    public long customerId() {
        return customerId;
    }

    public long amount() {
        return amount;
    }

    public OrderStatus status() {
        return status;
    }

    public String cancelReason() {
        return cancelReason;
    }
}
