package com.ddk.test.domain;

import com.ddk.core.domain.AggregateRoot;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;

/**
 * DDK 断言的入口，与 AssertJ 的 {@code Assertions} 并用。
 *
 * <pre>{@code
 * Order order = Order.place(customerId, lines);
 * assertThat(order).hasRaisedExactly(OrderPlacedEvent.class);
 *
 * order.clearEvents();
 * order.pay();
 * assertThatRejected(order::cancel).withCode(OrderError.ORDER_NOT_CANCELLABLE);
 * assertThat(order).hasRaised(OrderPaidEvent.class, event -> assertThat(event.orderId()).isEqualTo(order.id()));
 * }</pre>
 */
public final class DdkAssertions {

    private DdkAssertions() {
    }

    public static <A extends AggregateRoot<?>> AggregateAssert<A> assertThat(A aggregate) {
        return new AggregateAssert<>(aggregate);
    }

    /**
     * 断言一次操作被业务规则拒绝，即抛出了 {@code BusinessException}。
     */
    public static BusinessRuleAssert assertThatRejected(ThrowingCallable action) {
        return BusinessRuleAssert.of(action);
    }
}
