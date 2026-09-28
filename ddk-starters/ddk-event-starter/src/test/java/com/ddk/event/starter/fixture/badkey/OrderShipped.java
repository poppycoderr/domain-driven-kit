package com.ddk.event.starter.fixture.badkey;

import com.ddk.core.domain.IntegrationEvent;

@IntegrationEvent(value = "orders", key = "orderNo")
public record OrderShipped(
        String orderId
) {
}
