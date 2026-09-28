package com.ddk.event.starter.fixture.duplicate;

import com.ddk.core.domain.IntegrationEvent;

@IntegrationEvent("orders")
public record OrderClosed(
        String orderId
) {
}
