package com.example.mall.order.domain.model;

import com.ddk.core.domain.AggregateRoot;
import com.ddk.core.exception.BusinessException;
import com.example.mall.order.domain.error.OrderError;
import com.example.mall.order.domain.event.OrderCancelledEvent;
import com.example.mall.order.domain.event.OrderPlacedEvent;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * 订单聚合根。订单行是聚合的一部分，只能随订单一起创建和加载；金额由订单行算出，不接受外部传入。
 */
public class Order extends AggregateRoot<OrderId> {

    private Long customerId;

    private List<OrderLine> lines;

    private OrderStatus status;

    private Money totalAmount;

    private String cancelReason;

    private Order() {
    }

    /**
     * 下单并登记 {@link OrderPlacedEvent}。订单至少有一行，同一个 SKU 只能出现一次。
     */
    public static Order place(OrderId id, Long customerId, List<OrderLine> lines) {
        if (lines == null || lines.isEmpty()) {
            throw new BusinessException(OrderError.ORDER_EMPTY);
        }
        Set<String> skus = new HashSet<>();
        for (OrderLine line : lines) {
            if (!skus.add(line.skuId())) {
                throw new BusinessException(OrderError.DUPLICATE_SKU, line.skuId());
            }
        }
        Order order = new Order();
        order.assignId(Objects.requireNonNull(id, "id"));
        order.customerId = Objects.requireNonNull(customerId, "customerId");
        order.lines = List.copyOf(lines);
        order.status = OrderStatus.PENDING_PAYMENT;
        order.totalAmount = lines.stream().map(OrderLine::subtotal).reduce(Money.ZERO, Money::plus);
        order.registerEvent(new OrderPlacedEvent(id, customerId, order.totalAmount));
        return order;
    }

    /**
     * 从持久化数据重建，不产生领域事件，也不重新校验下单规则。
     */
    public static Order restore(OrderId id, Long customerId, List<OrderLine> lines, OrderStatus status, Money totalAmount,
                                String cancelReason, Long version) {
        Order order = new Order();
        order.assignId(Objects.requireNonNull(id, "id"));
        order.customerId = customerId;
        order.lines = List.copyOf(lines);
        order.status = status;
        order.totalAmount = totalAmount;
        order.cancelReason = cancelReason;
        order.assignVersion(version);
        return order;
    }

    /**
     * 取消订单。只有待支付的订单能取消。
     */
    public void cancel(String reason) {
        if (status != OrderStatus.PENDING_PAYMENT) {
            throw new BusinessException(OrderError.ORDER_NOT_CANCELLABLE, status);
        }
        this.status = OrderStatus.CANCELLED;
        this.cancelReason = reason;
        registerEvent(new OrderCancelledEvent(id(), reason));
    }

    public boolean belongsTo(Long customerId) {
        return this.customerId.equals(customerId);
    }

    public Long customerId() {
        return customerId;
    }

    public List<OrderLine> lines() {
        return lines;
    }

    public OrderStatus status() {
        return status;
    }

    public Money totalAmount() {
        return totalAmount;
    }

    public String cancelReason() {
        return cancelReason;
    }
}
