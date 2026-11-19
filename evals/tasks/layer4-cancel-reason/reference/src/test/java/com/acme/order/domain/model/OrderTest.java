package com.acme.order.domain.model;

import com.acme.order.domain.error.OrderError;
import com.acme.order.domain.event.OrderPlacedEvent;
import org.junit.jupiter.api.Test;

import static com.ddk.test.domain.DdkAssertions.assertThat;
import static com.ddk.test.domain.DdkAssertions.assertThatRejected;

class OrderTest {

    @Test
    void placingAnOrderRaisesAnEvent() {
        assertThat(Order.place(OrderId.of(1L), 7L, 100L)).hasRaisedExactly(OrderPlacedEvent.class);
    }

    @Test
    void anOrderNeedsAPositiveAmount() {
        assertThatRejected(() -> Order.place(OrderId.of(1L), 7L, 0L)).withCode(OrderError.INVALID_AMOUNT);
    }

    @Test
    void aCancelledOrderCannotBeCancelledAgain() {
        Order order = Order.place(OrderId.of(1L), 7L, 100L);
        order.cancel(null);
        assertThatRejected(() -> order.cancel("again")).withCode(OrderError.ORDER_NOT_CANCELLABLE);
    }

    @Test
    void aCancelReasonHasALimit() {
        Order order = Order.place(OrderId.of(1L), 7L, 100L);
        assertThatRejected(() -> order.cancel("x".repeat(201))).withCode(OrderError.CANCEL_REASON_TOO_LONG);
    }
}
