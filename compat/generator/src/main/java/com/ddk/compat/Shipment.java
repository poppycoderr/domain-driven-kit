package com.ddk.compat;

import com.ddk.core.domain.AggregateRoot;

import java.time.Instant;

public final class Shipment extends AggregateRoot<ShipmentId> {

    public static Shipment dispatch(ShipmentId id) {
        Shipment shipment = new Shipment();
        shipment.assignId(id);
        shipment.registerEvent(new ShipmentDispatched("evt-7", id, "tenant-a", "SF", Instant.parse("2026-10-22T08:00:00Z")));
        return shipment;
    }
}
