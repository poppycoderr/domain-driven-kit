package com.example.mall.inventory.domain.model;

import com.ddk.core.domain.AggregateRoot;
import com.ddk.core.exception.BusinessException;
import com.example.mall.inventory.domain.error.InventoryError;

import java.util.Objects;

/**
 * 一个 SKU 的库存。在库数量里有一部分已经被订单预占：可售数量 = 在库 - 预占，永远不能为负。
 * <p>
 * 预占只是把货留给某个订单，货还在仓库里；订单支付后才真正扣减在库数量。
 */
public class Stock extends AggregateRoot<SkuId> {

    private int onHand;

    private int reserved;

    private Stock() {
    }

    public static Stock restore(SkuId id, int onHand, int reserved, Long version) {
        Stock stock = new Stock();
        stock.assignId(Objects.requireNonNull(id, "id"));
        stock.onHand = onHand;
        stock.reserved = reserved;
        stock.assignVersion(version);
        return stock;
    }

    /**
     * 预占。可售数量不够时拒绝，不做部分预占。
     */
    public void reserve(int quantity) {
        requirePositive(quantity);
        if (quantity > available()) {
            throw new BusinessException(InventoryError.INSUFFICIENT_STOCK, id().value(), available(), quantity);
        }
        reserved += quantity;
    }

    /**
     * 释放预占：订单取消，货重新可售。
     */
    public void release(int quantity) {
        requireReserved(quantity);
        reserved -= quantity;
    }

    /**
     * 扣减：订单已支付，预占的货正式出库。
     */
    public void deduct(int quantity) {
        requireReserved(quantity);
        reserved -= quantity;
        onHand -= quantity;
    }

    public void restock(int quantity) {
        requirePositive(quantity);
        onHand += quantity;
    }

    public int onHand() {
        return onHand;
    }

    public int reserved() {
        return reserved;
    }

    public int available() {
        return onHand - reserved;
    }

    private void requireReserved(int quantity) {
        requirePositive(quantity);
        if (quantity > reserved) {
            throw new IllegalStateException("Stock " + id().value() + " has " + reserved + " reserved, cannot settle " + quantity);
        }
    }

    private static void requirePositive(int quantity) {
        if (quantity <= 0) {
            throw new BusinessException(InventoryError.INVALID_QUANTITY, quantity);
        }
    }
}
