package com.example.mall.inventory.domain.model;

import com.ddk.core.domain.Identifier;

/**
 * 库存预占记录的标识。
 */
public final class ReservationId extends Identifier<Long> {

    private ReservationId(Long value) {
        super(value);
    }

    public static ReservationId of(Long value) {
        return new ReservationId(value);
    }
}
