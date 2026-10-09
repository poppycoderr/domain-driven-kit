package com.example.mall.order.application.handler;

import com.ddk.projection.starter.Projections;
import com.example.mall.order.application.integration.OrderAwaitingPaymentMessage;
import com.example.mall.order.application.integration.OrderCancelledMessage;
import com.example.mall.order.application.integration.OrderPaidMessage;
import com.example.mall.order.application.integration.OrderPlacedMessage;
import com.example.mall.order.application.projection.OrderSearchProjection;
import com.example.mall.order.domain.event.OrderAwaitingPaymentEvent;
import com.example.mall.order.domain.event.OrderCancelledEvent;
import com.example.mall.order.domain.event.OrderPaidEvent;
import com.example.mall.order.domain.event.OrderPlacedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * 把订单的领域事件翻译成对外的消息。
 * <p>
 * 用同步的 {@code @EventListener}，在下单的事务里执行：消息在这里交给 Spring 的事件总线，DDK 把它登记进事件发布记录，
 * 和订单在同一个事务里提交，提交之后再投递。订单保存成功而消息丢失的情况因此不会出现。
 * <p>
 * 每个事件同时把订单标记为「搜索读模型需要刷新」。标记同样在这个事务里提交，刷新在提交之后进行。
 */
@Component
@RequiredArgsConstructor
public class OrderEventHandler {

    private final ApplicationEventPublisher publisher;

    private final Projections projections;

    @EventListener
    public void on(OrderPlacedEvent event) {
        projections.markDirty(OrderSearchProjection.NAME, event.orderId());
        publisher.publishEvent(new OrderPlacedMessage(UUID.randomUUID().toString(), event.orderId().value(), event.customerId(),
                event.lines().stream().map(line -> new OrderPlacedMessage.Line(line.skuId(), line.quantity())).toList()));
    }

    @EventListener
    public void on(OrderAwaitingPaymentEvent event) {
        projections.markDirty(OrderSearchProjection.NAME, event.orderId());
        publisher.publishEvent(new OrderAwaitingPaymentMessage(UUID.randomUUID().toString(), event.orderId().value(), event.customerId(),
                event.amount().amount(), event.expiresAt()));
    }

    @EventListener
    public void on(OrderPaidEvent event) {
        projections.markDirty(OrderSearchProjection.NAME, event.orderId());
        publisher.publishEvent(new OrderPaidMessage(UUID.randomUUID().toString(), event.orderId().value()));
    }

    @EventListener
    public void on(OrderCancelledEvent event) {
        projections.markDirty(OrderSearchProjection.NAME, event.orderId());
        publisher.publishEvent(new OrderCancelledMessage(UUID.randomUUID().toString(), event.orderId().value(), event.reason()));
    }
}
