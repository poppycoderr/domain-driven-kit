package com.example.mall.inventory.domain.event;

import com.ddk.core.domain.DomainEvent;
import com.example.mall.inventory.domain.model.SkuId;

import java.time.Instant;

/**
 * 订单预占的库存已释放。
 */
public record StockReleasedEvent(
        Long orderId,

        SkuId skuId,

        int quantity,

        Instant occurredOn
) implements DomainEvent {

    public StockReleasedEvent(Long orderId, SkuId skuId, int quantity) {
        this(orderId, skuId, quantity, Instant.now());
    }
}
