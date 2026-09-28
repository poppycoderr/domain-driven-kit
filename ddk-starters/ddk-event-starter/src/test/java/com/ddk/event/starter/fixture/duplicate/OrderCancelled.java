package com.ddk.event.starter.fixture.duplicate;

import com.ddk.core.domain.IntegrationEvent;

@IntegrationEvent(value = "orders", type = "OrderClosed")
public record OrderCancelled(
        String orderId
) {
}
