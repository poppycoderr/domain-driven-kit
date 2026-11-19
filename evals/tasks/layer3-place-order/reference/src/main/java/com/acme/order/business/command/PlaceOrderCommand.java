package com.acme.order.business.command;

/**
 * 下单。
 */
public record PlaceOrderCommand(
        long customerId,
        long amount) {
}
