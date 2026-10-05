package com.example.mall.inventory.domain.event;

import com.ddk.core.domain.DomainEvent;
import com.example.mall.inventory.domain.model.SkuId;

import java.time.Instant;

/**
 * 订单预占的库存已扣减出库。
 */
public record StockDeductedEvent(
        Long orderId,

        SkuId skuId,

        int quantity,

        Instant occurredOn
) implements DomainEvent {

    public StockDeductedEvent(Long orderId, SkuId skuId, int quantity) {
        this(orderId, skuId, quantity, Instant.now());
    }
}
