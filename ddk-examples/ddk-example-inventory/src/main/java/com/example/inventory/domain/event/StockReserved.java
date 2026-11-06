package com.example.inventory.domain.event;

import com.ddk.core.domain.DomainEvent;

import java.time.Instant;

/**
 * 库存已预留。{@code reservationId} 是客户端的请求号，同一次预留只有一个。
 */
public record StockReserved(
        String reservationId,
        Long skuId,
        int quantity,
        int remaining,
        Instant occurredOn
) implements DomainEvent {

    public StockReserved(String reservationId, Long skuId, int quantity, int remaining) {
        this(reservationId, skuId, quantity, remaining, Instant.now());
    }
}
