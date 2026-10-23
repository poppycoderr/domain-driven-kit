package com.ddk.compat;

import com.ddk.event.starter.consumer.IntegrationEventConsumer;
import com.ddk.event.starter.consumer.ReceivedEvent;

import java.util.ArrayList;
import java.util.List;

public class ShipmentTracker implements IntegrationEventConsumer<ShipmentDispatched> {

    public final List<ReceivedEvent<ShipmentDispatched>> received = new ArrayList<>();

    @Override
    public String group() {
        return "billing";
    }

    @Override
    public String source() {
        return "shipments:dispatched";
    }

    @Override
    public Class<ShipmentDispatched> payloadType() {
        return ShipmentDispatched.class;
    }

    @Override
    public boolean idempotent() {
        return true;
    }

    @Override
    public void handle(ReceivedEvent<ShipmentDispatched> event) {
        received.add(event);
    }
}
