package com.example.inventory.domain.model;

import com.ddk.core.domain.AggregateRoot;
import com.ddk.core.exception.BusinessException;
import com.example.inventory.domain.error.InventoryError;
import com.example.inventory.domain.event.StockReserved;

import java.util.Objects;

/**
 * 一个商品的可售库存。预留只减少库存，不会减到负数。
 */
public class Sku extends AggregateRoot<SkuId> {

    private final String name;

    private int available;

    private Sku(SkuId id, String name, int available, Long version) {
        super(id);
        this.name = name;
        this.available = available;
        assignVersion(version);
    }

    public static Sku restore(SkuId id, String name, int available, Long version) {
        return new Sku(id, name, available, version);
    }

    public void reserve(String reservationId, int quantity) {
        if (quantity <= 0) {
            throw new BusinessException(InventoryError.INVALID_QUANTITY, quantity);
        }
        if (quantity > available) {
            throw new BusinessException(InventoryError.OUT_OF_STOCK, skuId().value(), available);
        }
        available -= quantity;
        registerEvent(new StockReserved(reservationId, skuId().value(), quantity, available));
    }

    public SkuId skuId() {
        return Objects.requireNonNull(id());
    }

    public String name() {
        return name;
    }

    public int available() {
        return available;
    }
}
