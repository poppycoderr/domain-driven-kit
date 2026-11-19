package com.acme.order.application.command;

/**
 * 下单。
 */
public record PlaceOrderCommand(
        long customerId,
        long amount) {
}
