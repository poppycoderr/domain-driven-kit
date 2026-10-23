package com.ddk.compat;

import com.ddk.core.domain.DomainEvent;
import com.ddk.core.domain.IntegrationEvent;

import java.time.Instant;

@IntegrationEvent(value = "shipments:dispatched", key = "shipmentId", id = "eventId", type = "shipment.dispatched", version = 2,
        tenant = "tenantId")
public record ShipmentDispatched(
        String eventId,
        ShipmentId shipmentId,
        String tenantId,
        String carrier,
        Instant occurredOn
) implements DomainEvent {
}
