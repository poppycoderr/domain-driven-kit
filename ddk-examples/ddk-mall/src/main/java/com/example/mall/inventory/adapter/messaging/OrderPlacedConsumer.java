package com.example.mall.inventory.adapter.messaging;

import com.ddk.event.starter.consumer.IntegrationEventConsumer;
import com.ddk.event.starter.consumer.ReceivedEvent;
import com.example.mall.inventory.adapter.messaging.payload.OrderPlacedPayload;
import com.example.mall.inventory.application.command.ReserveStockCommand;
import com.example.mall.inventory.application.service.InventoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Objects;

/**
 * 消费订单上下文的「订单已下单」：为订单预占库存。
 * <p>
 * 这里没有用 {@code idempotent()}：预占要先锁住 SKU 再开事务，去重登记必须写在锁和事务的里面，所以由应用服务自己调用
 * {@code IdempotentConsumer}，事件标识作为参数传进去。
 */
@Component
@RequiredArgsConstructor
public class OrderPlacedConsumer implements IntegrationEventConsumer<OrderPlacedPayload> {

    private final InventoryService inventoryService;

    @Override
    public String group() {
        return "mall-inventory";
    }

    @Override
    public String source() {
        return "mall-order-events:placed";
    }

    @Override
    public Class<OrderPlacedPayload> payloadType() {
        return OrderPlacedPayload.class;
    }

    @Override
    public void handle(ReceivedEvent<OrderPlacedPayload> event) {
        OrderPlacedPayload payload = event.payload();
        inventoryService.reserveForOrder(Objects.requireNonNull(event.eventId(), "eventId"), new ReserveStockCommand(payload.orderId(),
                payload.lines().stream().map(line -> new ReserveStockCommand.Line(line.skuId(), line.quantity())).toList()));
    }
}
