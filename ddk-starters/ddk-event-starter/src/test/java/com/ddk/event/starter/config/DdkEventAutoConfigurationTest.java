package com.ddk.event.starter.config;

import com.ddk.core.domain.AbstractDomainEvent;
import com.ddk.core.domain.AggregateRoot;
import com.ddk.core.domain.DomainEvent;
import com.ddk.core.domain.DomainEventPublisher;
import com.ddk.core.domain.Identifier;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.event.EventListener;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("领域事件 starter 自动装配")
class DdkEventAutoConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(DdkEventAutoConfiguration.class));

    static final class OrderId extends Identifier<Long> {
        private OrderId(Long value) {
            super(value);
        }

        static OrderId of(Long value) {
            return new OrderId(value);
        }
    }

    static final class OrderPaidEvent extends AbstractDomainEvent {
        private final OrderId orderId;

        OrderPaidEvent(OrderId orderId) {
            this.orderId = orderId;
        }

        OrderId orderId() {
            return orderId;
        }
    }

    static final class Order extends AggregateRoot<OrderId> {
        Order(OrderId id) {
            super(id);
        }

        void markPaid() {
            registerEvent(new OrderPaidEvent(id()));
        }
    }

    /**
     * 收集所有 DomainEvent，验证事件真的走到了 Spring 的事件总线。
     * <p>
     * 领域事件不是 {@code ApplicationEvent} 的子类，Spring 会把它包进
     * {@code PayloadApplicationEvent}；用 {@code @EventListener} 声明载荷类型即可，
     * 订阅方不需要感知这层包装。
     */
    static final class RecordingListener {
        final List<DomainEvent> received = new ArrayList<>();

        @EventListener
        void on(DomainEvent event) {
            received.add(event);
        }
    }

    @Configuration
    static class ListenerConfig {
        @Bean
        RecordingListener recordingListener() {
            return new RecordingListener();
        }
    }

    @Test
    @DisplayName("默认注册 DomainEventPublisher")
    void registersPublisherByDefault() {
        runner.run(context -> assertThat(context)
                .hasSingleBean(DomainEventPublisher.class)
                .getBean(DomainEventPublisher.class)
                .isInstanceOf(SpringDomainEventPublisher.class));
    }

    @Test
    @DisplayName("ddk.event.enabled=false 时不注册，仓储会静默跳过事件发布")
    void canBeDisabled() {
        runner.withPropertyValues("ddk.event.enabled=false")
                .run(context -> assertThat(context).doesNotHaveBean(DomainEventPublisher.class));
    }

    @Test
    @DisplayName("使用方自定义的发布器优先")
    void userBeanWins() {
        DomainEventPublisher custom = event -> {
        };
        runner.withBean(DomainEventPublisher.class, () -> custom)
                .run(context -> assertThat(context.getBean(DomainEventPublisher.class)).isSameAs(custom));
    }

    @Test
    @DisplayName("事件经由 Spring 事件总线送达订阅方")
    void publishesToApplicationEventBus() {
        runner.withUserConfiguration(ListenerConfig.class).run(context -> {
            DomainEventPublisher publisher = context.getBean(DomainEventPublisher.class);
            RecordingListener listener = context.getBean(RecordingListener.class);

            publisher.publish(new OrderPaidEvent(OrderId.of(1L)));

            assertThat(listener.received).hasSize(1);
            assertThat(listener.received.getFirst()).isInstanceOf(OrderPaidEvent.class);
        });
    }

    @Test
    @DisplayName("publishEventsOf 排空聚合上的事件，一次全部发出")
    void drainsAggregate() {
        runner.withUserConfiguration(ListenerConfig.class).run(context -> {
            DomainEventPublisher publisher = context.getBean(DomainEventPublisher.class);
            RecordingListener listener = context.getBean(RecordingListener.class);

            Order order = new Order(OrderId.of(1L));
            order.markPaid();
            order.markPaid();

            publisher.publishEventsOf(order);

            assertThat(listener.received).hasSize(2);
            assertThat(order.hasDomainEvents()).as("排空后聚合上不应再有待发布事件").isFalse();

            publisher.publishEventsOf(order);
            assertThat(listener.received).as("再次调用不应重复发布").hasSize(2);
        });
    }

    @Test
    @DisplayName("publishEventsOf 对 null 与无事件的聚合都安全")
    void nullSafe() {
        runner.run(context -> {
            DomainEventPublisher publisher = context.getBean(DomainEventPublisher.class);
            publisher.publishEventsOf(null);
            publisher.publishEventsOf(new Order(OrderId.of(1L)));
            publisher.publish(null);
        });
    }
}
