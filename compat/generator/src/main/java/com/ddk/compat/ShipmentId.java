package com.ddk.compat;

import com.ddk.core.domain.Identifier;

public final class ShipmentId extends Identifier<Long> {

    private ShipmentId(Long value) {
        super(value);
    }

    public static ShipmentId of(Long value) {
        return new ShipmentId(value);
    }
}
