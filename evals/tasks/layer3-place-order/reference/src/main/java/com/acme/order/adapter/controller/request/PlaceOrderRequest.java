package com.acme.order.adapter.controller.request;

import com.acme.order.business.command.PlaceOrderCommand;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * 下单请求。
 */
public record PlaceOrderRequest(
        @NotNull @Positive Long customerId,
        @NotNull @Positive Long amount) {

    public PlaceOrderCommand toCommand() {
        return new PlaceOrderCommand(customerId, amount);
    }
}
