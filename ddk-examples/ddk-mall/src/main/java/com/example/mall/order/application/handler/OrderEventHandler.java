package com.example.mall.order.application.handler;

import com.example.mall.order.domain.event.OrderCancelledEvent;
import com.example.mall.order.domain.event.OrderPlacedEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 订单领域事件的进程内订阅方，只在事务提交后执行。
 */
@Slf4j
@Component
public class OrderEventHandler {

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void on(OrderPlacedEvent event) {
        log.info("Order placed: id={}, customer={}, total={}", event.orderId().value(), event.customerId(), event.totalAmount().amount());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void on(OrderCancelledEvent event) {
        log.info("Order cancelled: id={}, reason={}", event.orderId().value(), event.reason());
    }
}
