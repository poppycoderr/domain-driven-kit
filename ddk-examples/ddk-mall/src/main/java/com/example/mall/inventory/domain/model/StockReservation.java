package com.example.mall.inventory.domain.model;

import com.ddk.core.domain.AggregateRoot;
import com.ddk.core.exception.BusinessException;
import com.example.mall.inventory.domain.error.InventoryError;
import com.example.mall.inventory.domain.event.StockDeductedEvent;
import com.example.mall.inventory.domain.event.StockReleasedEvent;
import com.example.mall.inventory.domain.event.StockReservedEvent;

import java.util.Objects;

/**
 * 一个订单对一个 SKU 的预占记录。它记下「这部分预占是谁的」，释放和扣减因此可以重复执行而不会算错：
 * 同一个订单再来一次释放，记录已经是已释放状态，什么都不会发生。
 * <p>
 * 订单在这里只是一个编号，库存上下文不认识订单上下文里的任何类型。
 */
public class StockReservation extends AggregateRoot<ReservationId> {

    private Long orderId;

    private SkuId skuId;

    private int quantity;

    private ReservationStatus status;

    private StockReservation() {
    }

    public static StockReservation reserve(ReservationId id, Long orderId, SkuId skuId, int quantity) {
        StockReservation reservation = new StockReservation();
        reservation.assignId(Objects.requireNonNull(id, "id"));
        reservation.orderId = Objects.requireNonNull(orderId, "orderId");
        reservation.skuId = Objects.requireNonNull(skuId, "skuId");
        reservation.quantity = quantity;
        reservation.status = ReservationStatus.RESERVED;
        reservation.registerEvent(new StockReservedEvent(orderId, skuId, quantity));
        return reservation;
    }

    public static StockReservation restore(ReservationId id, Long orderId, SkuId skuId, int quantity, ReservationStatus status, Long version) {
        StockReservation reservation = new StockReservation();
        reservation.assignId(Objects.requireNonNull(id, "id"));
        reservation.orderId = orderId;
        reservation.skuId = skuId;
        reservation.quantity = quantity;
        reservation.status = status;
        reservation.assignVersion(version);
        return reservation;
    }

    /**
     * 释放。已经释放过的返回 false，调用方据此跳过库存数量的调整；已经扣减的不能再释放。
     */
    public boolean release() {
        if (status == ReservationStatus.RELEASED) {
            return false;
        }
        if (status == ReservationStatus.CONFIRMED) {
            throw new BusinessException(InventoryError.RESERVATION_ALREADY_CONFIRMED, orderId);
        }
        status = ReservationStatus.RELEASED;
        registerEvent(new StockReleasedEvent(orderId, skuId, quantity));
        return true;
    }

    /**
     * 扣减。已经扣减过的返回 false；已经释放的不能再扣减。
     */
    public boolean confirm() {
        if (status == ReservationStatus.CONFIRMED) {
            return false;
        }
        if (status == ReservationStatus.RELEASED) {
            throw new BusinessException(InventoryError.RESERVATION_ALREADY_RELEASED, orderId);
        }
        status = ReservationStatus.CONFIRMED;
        registerEvent(new StockDeductedEvent(orderId, skuId, quantity));
        return true;
    }

    public Long orderId() {
        return orderId;
    }

    public SkuId skuId() {
        return skuId;
    }

    public int quantity() {
        return quantity;
    }

    public ReservationStatus status() {
        return status;
    }
}
