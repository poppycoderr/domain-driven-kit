package com.example.mall.order.domain;

import com.example.mall.order.domain.error.OrderError;
import com.example.mall.order.domain.event.OrderAwaitingPaymentEvent;
import com.example.mall.order.domain.event.OrderCancelledEvent;
import com.example.mall.order.domain.event.OrderPaidEvent;
import com.example.mall.order.domain.event.OrderPlacedEvent;
import com.example.mall.order.domain.model.Money;
import com.example.mall.order.domain.model.Order;
import com.example.mall.order.domain.model.OrderId;
import com.example.mall.order.domain.model.OrderLine;
import com.example.mall.order.domain.model.OrderStatus;
import com.ddk.test.domain.DdkAssertions;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static com.ddk.test.domain.DdkAssertions.assertThatRejected;
import static org.assertj.core.api.Assertions.assertThat;

class OrderTest {

    private static final Instant DEADLINE = Instant.parse("2030-01-01T00:30:00Z");

    private static final OrderLine KEYBOARD = new OrderLine("SKU-KEYBOARD", "机械键盘", Money.of("399.00"), 1);

    private static final OrderLine MOUSE = new OrderLine("SKU-MOUSE", "无线鼠标", Money.of("129.00"), 2);

    @Test
    void placingAnOrderTotalsItsLinesAndRecordsTheEvent() {
        Order order = Order.place(OrderId.of(1L), 7L, List.of(KEYBOARD, MOUSE), DEADLINE);

        assertThat(order.status()).isEqualTo(OrderStatus.PENDING_STOCK);
        assertThat(order.totalAmount()).isEqualTo(Money.of("657.00"));
        DdkAssertions.assertThat(order)
                .hasRaisedExactly(OrderPlacedEvent.class)
                .hasRaised(OrderPlacedEvent.class, event -> assertThat(event.totalAmount().amount()).isEqualByComparingTo("657"));
    }

    @Test
    void anOrderNeedsAtLeastOneLineAndDistinctSkus() {
        assertThatRejected(() -> Order.place(OrderId.of(1L), 7L, List.of(), DEADLINE)).withCode(OrderError.ORDER_EMPTY);
        assertThatRejected(() -> Order.place(OrderId.of(1L), 7L, List.of(KEYBOARD, KEYBOARD), DEADLINE))
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
        Order order = Order.place(OrderId.of(1L), 7L, List.of(KEYBOARD), DEADLINE);
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
        Order order = Order.place(OrderId.of(1L), 7L, List.of(KEYBOARD), DEADLINE);
        order.clearEvents();

        assertThat(order.confirmStock()).isTrue();
        assertThat(order.status()).isEqualTo(OrderStatus.PENDING_PAYMENT);
        assertThat(order.confirmStock()).isFalse();
        DdkAssertions.assertThat(order)
                .hasRaisedExactly(OrderAwaitingPaymentEvent.class)
                .hasRaised(OrderAwaitingPaymentEvent.class, event -> {
                    assertThat(event.amount()).isEqualTo(Money.of("399.00"));
                    assertThat(event.expiresAt()).isEqualTo(DEADLINE);
                });

        order.cancel("不想要了");
        assertThat(order.confirmStock()).isFalse();
        assertThat(order.status()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(order.isOpen()).isFalse();
    }

    @Test
    void onlyAnOrderAwaitingPaymentCanBePaidAndAPaidOrderCannotBeCancelled() {
        Order order = Order.place(OrderId.of(1L), 7L, List.of(KEYBOARD), DEADLINE);
        assertThat(order.pay()).as("stock is not confirmed yet").isFalse();
        order.confirmStock();
        order.clearEvents();

        assertThat(order.pay()).isTrue();
        assertThat(order.status()).isEqualTo(OrderStatus.PAID);
        assertThat(order.pay()).as("a repeated notification changes nothing").isFalse();
        DdkAssertions.assertThat(order).hasRaisedExactly(OrderPaidEvent.class);
        assertThat(order.isOpen()).isFalse();
        assertThat(order.isCancelled()).isFalse();
        assertThatRejected(() -> order.cancel("付完又不想要了")).withCode(OrderError.ORDER_NOT_CANCELLABLE);
    }

    @Test
    void anOpenOrderExpiresAtItsDeadlineAndASettledOneNever() {
        Order order = Order.place(OrderId.of(1L), 7L, List.of(KEYBOARD), DEADLINE);
        assertThat(order.isExpired(DEADLINE.minusSeconds(1))).isFalse();
        assertThat(order.isExpired(DEADLINE)).isTrue();

        order.confirmStock();
        assertThat(order.isExpired(DEADLINE.plusSeconds(1))).isTrue();
        order.pay();
        assertThat(order.isExpired(DEADLINE.plusSeconds(1))).isFalse();
        assertThat(order.expiresAt()).isEqualTo(DEADLINE);
    }

    @Test
    void restoringAnOrderRaisesNoEvents() {
        Order order = Order.restore(OrderId.of(1L), 7L, List.of(KEYBOARD), OrderStatus.CANCELLED, Money.of("399.00"), "超时", DEADLINE, 3L);

        DdkAssertions.assertThat(order).hasRaisedNoEvents();
        assertThat(order.version()).isEqualTo(3L);
        assertThat(order.belongsTo(7L)).isTrue();
        assertThat(order.belongsTo(8L)).isFalse();
    }
}
