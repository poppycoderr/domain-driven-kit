package com.example.mall.inventory.application.response;

import com.example.mall.inventory.domain.model.StockReservation;

/**
 * 预占记录对外响应。
 */
public record ReservationResponse(
        Long orderId,

        String skuId,

        int quantity,

        String status
) {

    public static ReservationResponse from(StockReservation reservation) {
        return new ReservationResponse(reservation.orderId(), reservation.skuId().value(), reservation.quantity(), reservation.status().name());
    }
}
