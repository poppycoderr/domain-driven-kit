package com.example.mall.order.domain;

import com.example.mall.order.domain.error.OrderError;
import com.example.mall.order.domain.event.OrderCancelledEvent;
import com.example.mall.order.domain.event.OrderPlacedEvent;
import com.example.mall.order.domain.model.Money;
import com.example.mall.order.domain.model.Order;
import com.example.mall.order.domain.model.OrderId;
import com.example.mall.order.domain.model.OrderLine;
import com.example.mall.order.domain.model.OrderStatus;
import com.ddk.test.domain.DdkAssertions;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static com.ddk.test.domain.DdkAssertions.assertThatRejected;
import static org.assertj.core.api.Assertions.assertThat;

class OrderTest {

    private static final OrderLine KEYBOARD = new OrderLine("SKU-KEYBOARD", "机械键盘", Money.of("399.00"), 1);

    private static final OrderLine MOUSE = new OrderLine("SKU-MOUSE", "无线鼠标", Money.of("129.00"), 2);

    @Test
    void placingAnOrderTotalsItsLinesAndRecordsTheEvent() {
        Order order = Order.place(OrderId.of(1L), 7L, List.of(KEYBOARD, MOUSE));

        assertThat(order.status()).isEqualTo(OrderStatus.PENDING_STOCK);
        assertThat(order.totalAmount()).isEqualTo(Money.of("657.00"));
        DdkAssertions.assertThat(order)
                .hasRaisedExactly(OrderPlacedEvent.class)
                .hasRaised(OrderPlacedEvent.class, event -> assertThat(event.totalAmount().amount()).isEqualByComparingTo("657"));
    }

    @Test
    void anOrderNeedsAtLeastOneLineAndDistinctSkus() {
        assertThatRejected(() -> Order.place(OrderId.of(1L), 7L, List.of())).withCode(OrderError.ORDER_EMPTY);
        assertThatRejected(() -> Order.place(OrderId.of(1L), 7L, List.of(KEYBOARD, KEYBOARD)))
                .withCode(OrderError.DUPLICATE_SKU)
                .withArgs("SKU-KEYBOARD");
    }

    @Test
    void quantitiesAndAmountsAreValidatedByTheValueObjects() {
        assertThatRejected(() -> new OrderLine("SKU-MOUSE", "无线鼠标", Money.of("129.00"), 0)).withCode(OrderError.INVALID_QUANTITY);
        assertThatRejected(() -> Money.of("-1")).withCode(OrderError.INVALID_AMOUNT);
        assertThatRejected(() -> Money.of("1.234")).withCode(OrderError.INVALID_AMOUNT);
        assertThat(new Money(new BigDecimal("12.5")).amount()).isEqualTo(new BigDecimal("12.50"));
    }

    @Test
    void onlyPendingOrdersCanBeCancelled() {
        Order order = Order.place(OrderId.of(1L), 7L, List.of(KEYBOARD));
        order.clearEvents();

        order.cancel("不想要了");
        assertThat(order.status()).isEqualTo(OrderStatus.CANCELLED);
        DdkAssertions.assertThat(order).hasRaised(OrderCancelledEvent.class, event -> assertThat(event.reason()).isEqualTo("不想要了"));

        order.clearEvents();
        assertThatRejected(() -> order.cancel("再取消一次"))
                .withCode(OrderError.ORDER_NOT_CANCELLABLE)
                .withoutRaisingEventsOn(order);
    }

    @Test
    void stockConfirmationMovesAWaitingOrderToPaymentAndIsIgnoredOtherwise() {
        Order order = Order.place(OrderId.of(1L), 7L, List.of(KEYBOARD));
        order.clearEvents();

        assertThat(order.confirmStock()).isTrue();
        assertThat(order.status()).isEqualTo(OrderStatus.PENDING_PAYMENT);
        assertThat(order.confirmStock()).isFalse();
        DdkAssertions.assertThat(order).hasRaisedNoEvents();

        order.cancel("不想要了");
        assertThat(order.confirmStock()).isFalse();
        assertThat(order.status()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(order.isOpen()).isFalse();
    }

    @Test
    void restoringAnOrderRaisesNoEvents() {
        Order order = Order.restore(OrderId.of(1L), 7L, List.of(KEYBOARD), OrderStatus.CANCELLED, Money.of("399.00"), "超时", 3L);

        DdkAssertions.assertThat(order).hasRaisedNoEvents();
        assertThat(order.version()).isEqualTo(3L);
        assertThat(order.belongsTo(7L)).isTrue();
        assertThat(order.belongsTo(8L)).isFalse();
    }
}
