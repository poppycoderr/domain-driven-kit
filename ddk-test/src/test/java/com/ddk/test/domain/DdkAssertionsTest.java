package com.ddk.test.domain;

import com.ddk.core.domain.AggregateRoot;
import com.ddk.core.domain.DomainEvent;
import com.ddk.core.domain.Identifier;
import com.ddk.core.exception.BusinessException;
import com.ddk.core.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static com.ddk.test.domain.DdkAssertions.assertThatRejected;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("聚合与业务规则断言")
class DdkAssertionsTest {

    @Test
    @DisplayName("事件断言：登记过、恰好这些、没登记过")
    void eventAssertionsPass() {
        Order order = Order.place(OrderId.of(1L));
        order.pay();

        DdkAssertions.assertThat(order)
                .hasRaised(OrderPaid.class)
                .hasRaised(OrderPlaced.class, event -> assertThat(event.orderId()).isEqualTo(OrderId.of(1L)))
                .hasRaisedExactly(OrderPlaced.class, OrderPaid.class)
                .hasNotRaised(OrderCancelled.class);

        order.clearEvents();
        DdkAssertions.assertThat(order).hasRaisedNoEvents();
    }

    @Test
    @DisplayName("事件断言失败时，消息里列出聚合实际登记的事件")
    void eventAssertionsExplainFailures() {
        Order order = Order.place(OrderId.of(1L));

        assertThatThrownBy(() -> DdkAssertions.assertThat(order).hasRaised(OrderPaid.class))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("Expected Order to have raised OrderPaid, but its events were [OrderPlaced]");
        assertThatThrownBy(() -> DdkAssertions.assertThat(order).hasRaisedExactly(OrderPaid.class))
                .hasMessageContaining("exactly [OrderPaid], but its events were [OrderPlaced]");
        assertThatThrownBy(() -> DdkAssertions.assertThat(order).hasNotRaised(OrderPlaced.class))
                .hasMessageContaining("not to have raised OrderPlaced");
        assertThatThrownBy(() -> DdkAssertions.assertThat(order).hasRaisedNoEvents())
                .hasMessageContaining("no events, but its events were [OrderPlaced]");
        assertThatThrownBy(() -> DdkAssertions.assertThat(order).hasRaised(OrderPaid.class, event -> {
        })).hasMessageContaining("exactly one OrderPaid");
        assertThatThrownBy(() -> DdkAssertions.assertThat(order)
                .hasRaised(OrderPlaced.class, event -> assertThat(event.orderId()).isEqualTo(OrderId.of(2L))))
                .isInstanceOf(AssertionError.class);
    }

    @Test
    @DisplayName("规则断言：按错误码与参数，被拒绝的操作不留下事件")
    void ruleAssertionsPass() {
        Order order = Order.place(OrderId.of(1L));
        order.pay();
        order.clearEvents();

        assertThatRejected(order::cancel)
                .withCode(OrderError.ORDER_NOT_CANCELLABLE)
                .withArgs("PAID")
                .withoutRaisingEventsOn(order);
    }

    @Test
    @DisplayName("规则断言失败：操作成功了、抛的不是业务异常、错误码或参数不对")
    void ruleAssertionsExplainFailures() {
        Order paid = Order.place(OrderId.of(1L));
        paid.pay();

        assertThatThrownBy(() -> assertThatRejected(() -> Order.place(OrderId.of(2L)).cancel()))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("but it succeeded");
        assertThatThrownBy(() -> assertThatRejected(() -> {
            throw new IllegalStateException("boom");
        })).isInstanceOf(AssertionError.class).hasMessageContaining("but it threw java.lang.IllegalStateException: boom");
        assertThatThrownBy(() -> assertThatRejected(paid::cancel).withCode(OrderError.ORDER_NOT_FOUND))
                .hasMessageContaining("Expected rejection with code ORDER_NOT_FOUND, but the code was ORDER_NOT_CANCELLABLE");
        assertThatThrownBy(() -> assertThatRejected(paid::cancel).withArgs("PENDING"))
                .hasMessageContaining("Expected rejection with args [PENDING], but the args were [PAID]");
        assertThatThrownBy(() -> assertThatRejected(paid::cancel).withoutRaisingEventsOn(paid))
                .hasMessageContaining("no events");
    }

    enum OrderError implements ErrorCode {
        ORDER_NOT_FOUND("订单不存在"),
        ORDER_NOT_CANCELLABLE("当前状态不能取消：{0}");

        private final String message;

        OrderError(String message) {
            this.message = message;
        }

        @Override
        public String getMessage() {
            return message;
        }
    }

    static final class OrderId extends Identifier<Long> {

        private OrderId(Long value) {
            super(value);
        }

        static OrderId of(Long value) {
            return new OrderId(value);
        }
    }

    record OrderPlaced(
            OrderId orderId,

            Instant occurredOn
    ) implements DomainEvent {
    }

    record OrderPaid(
            OrderId orderId,

            Instant occurredOn
    ) implements DomainEvent {
    }

    record OrderCancelled(
            OrderId orderId,

            Instant occurredOn
    ) implements DomainEvent {
    }

    static final class Order extends AggregateRoot<OrderId> {

        private String status = "PENDING";

        private Order(OrderId id) {
            super(id);
        }

        static Order place(OrderId id) {
            Order order = new Order(id);
            order.registerEvent(new OrderPlaced(id, Instant.EPOCH));
            return order;
        }

        void pay() {
            status = "PAID";
            registerEvent(new OrderPaid(id(), Instant.EPOCH));
        }

        void cancel() {
            if (!"PENDING".equals(status)) {
                throw new BusinessException(OrderError.ORDER_NOT_CANCELLABLE, status);
            }
            status = "CANCELLED";
            registerEvent(new OrderCancelled(id(), Instant.EPOCH));
        }
    }
}
