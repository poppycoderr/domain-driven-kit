package com.example.mall.inventory.domain.event;

import com.ddk.core.domain.DomainEvent;
import com.example.mall.inventory.domain.model.SkuId;

import java.time.Instant;

/**
 * 库存已为订单预占。
 */
public record StockReservedEvent(
        Long orderId,

        SkuId skuId,

        int quantity,

        Instant occurredOn
) implements DomainEvent {

    public StockReservedEvent(Long orderId, SkuId skuId, int quantity) {
        this(orderId, skuId, quantity, Instant.now());
    }
}
