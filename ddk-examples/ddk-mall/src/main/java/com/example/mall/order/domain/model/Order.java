package com.example.mall.order.domain.model;

import com.ddk.core.domain.AggregateRoot;
import com.ddk.core.exception.BusinessException;
import com.example.mall.order.domain.error.OrderError;
import com.example.mall.order.domain.event.OrderAwaitingPaymentEvent;
import com.example.mall.order.domain.event.OrderCancelledEvent;
import com.example.mall.order.domain.event.OrderPaidEvent;
import com.example.mall.order.domain.event.OrderPlacedEvent;

import java.time.Instant;
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

    private Instant expiresAt;

    private Order() {
    }

    /**
     * 下单并登记 {@link OrderPlacedEvent}。订单至少有一行，同一个 SKU 只能出现一次。
     *
     * @param expiresAt 支付期限，到这个时刻还没有支付的订单会被关闭
     */
    public static Order place(OrderId id, Long customerId, List<OrderLine> lines, Instant expiresAt) {
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
        order.status = OrderStatus.PENDING_STOCK;
        order.expiresAt = Objects.requireNonNull(expiresAt, "expiresAt");
        order.totalAmount = lines.stream().map(OrderLine::subtotal).reduce(Money.ZERO, Money::plus);
        order.registerEvent(new OrderPlacedEvent(id, customerId, order.totalAmount, order.lines));
        return order;
    }

    /**
     * 从持久化数据重建，不产生领域事件，也不重新校验下单规则。
     */
    public static Order restore(OrderId id, Long customerId, List<OrderLine> lines, OrderStatus status, Money totalAmount,
                                String cancelReason, Instant expiresAt, Long version) {
        Order order = new Order();
        order.assignId(Objects.requireNonNull(id, "id"));
        order.customerId = customerId;
        order.lines = List.copyOf(lines);
        order.status = status;
        order.totalAmount = totalAmount;
        order.cancelReason = cancelReason;
        order.expiresAt = expiresAt;
        order.assignVersion(version);
        return order;
    }

    /**
     * 库存已为这个订单预占，可以支付了，登记 {@link OrderAwaitingPaymentEvent}。
     * <p>
     * 只对等待库存确认的订单生效，返回是否发生了变化。订单可能在库存确认回来之前已经被取消，或者同一个确认被送达了两次，
     * 这两种情况都不是错误，什么都不做即可。
     */
    public boolean confirmStock() {
        if (status != OrderStatus.PENDING_STOCK) {
            return false;
        }
        this.status = OrderStatus.PENDING_PAYMENT;
        registerEvent(new OrderAwaitingPaymentEvent(id(), customerId, totalAmount, expiresAt));
        return true;
    }

    /**
     * 支付已完成。只对等待支付的订单生效，返回是否发生了变化：重复的支付通知，或者订单已经取消之后才到的支付通知，都返回 false。
     */
    public boolean pay() {
        if (status != OrderStatus.PENDING_PAYMENT) {
            return false;
        }
        this.status = OrderStatus.PAID;
        registerEvent(new OrderPaidEvent(id()));
        return true;
    }

    /**
     * 取消订单。还没有支付的订单才能取消。
     */
    public void cancel(String reason) {
        if (!isOpen()) {
            throw new BusinessException(OrderError.ORDER_NOT_CANCELLABLE, status);
        }
        this.status = OrderStatus.CANCELLED;
        this.cancelReason = reason;
        registerEvent(new OrderCancelledEvent(id(), reason));
    }

    /**
     * 订单还在进行中：等待库存确认或等待支付。
     */
    public boolean isOpen() {
        return status == OrderStatus.PENDING_STOCK || status == OrderStatus.PENDING_PAYMENT;
    }

    public boolean isCancelled() {
        return status == OrderStatus.CANCELLED;
    }

    /**
     * 订单还在进行中，但已经过了支付期限。
     */
    public boolean isExpired(Instant now) {
        return isOpen() && expiresAt != null && !now.isBefore(expiresAt);
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

    public Instant expiresAt() {
        return expiresAt;
    }
}
