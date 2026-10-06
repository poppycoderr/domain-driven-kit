package com.ddk.event.starter.config;

import com.ddk.event.starter.consumer.IntegrationEventConsumer;
import com.ddk.event.starter.consumer.IntegrationEventDispatcher;
import com.ddk.event.starter.consumer.ReceivedEvent;
import com.ddk.event.starter.internal.LocalEventDelivery;
import com.ddk.event.starter.internal.RocketMqEventConsumers;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("集成事件消费端自动装配")
class DdkEventConsumerAutoConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(DdkEventAutoConfiguration.class, DdkEventConsumerAutoConfiguration.class,
                    DdkRocketMqEventConsumerAutoConfiguration.class));

    @Test
    @DisplayName("没有声明消费方时什么都不注册")
    void nothingWithoutConsumers() {
        runner.withPropertyValues("ddk.event.local-delivery.enabled=true", "ddk.event.rocketmq.name-server=127.0.0.1:9876")
                .run(context -> assertThat(context)
                        .doesNotHaveBean(IntegrationEventDispatcher.class)
                        .doesNotHaveBean(LocalEventDelivery.class)
                        .doesNotHaveBean(RocketMqEventConsumers.class));
    }

    @Test
    @DisplayName("声明了消费方就注册分发器；进程内转发和 RocketMQ 消费都要各自的配置才启用")
    void dispatcherOnlyByDefault() {
        runner.withUserConfiguration(Consumers.class).run(context -> {
            assertThat(context).hasSingleBean(IntegrationEventDispatcher.class)
                    .doesNotHaveBean(LocalEventDelivery.class)
                    .doesNotHaveBean(RocketMqEventConsumers.class);
            assertThat(context.getBean(IntegrationEventDispatcher.class).consumers()).hasSize(1);
        });
        runner.withUserConfiguration(Consumers.class).withPropertyValues("ddk.event.local-delivery.enabled=true")
                .run(context -> assertThat(context).hasSingleBean(LocalEventDelivery.class));
    }

    @Test
    @DisplayName("配置了 NameServer 也可以单独关掉消费")
    void rocketMqConsumptionCanBeTurnedOff() {
        runner.withUserConfiguration(Consumers.class)
                .withPropertyValues("ddk.event.rocketmq.name-server=127.0.0.1:9876", "ddk.event.rocketmq.consumer.enabled=false")
                .run(context -> assertThat(context).doesNotHaveBean(RocketMqEventConsumers.class));
    }

    @Test
    @DisplayName("要求去重的消费方在没有开启去重表时让启动失败")
    void idempotentConsumerNeedsTheInbox() {
        runner.withUserConfiguration(IdempotentConsumers.class).run(context -> assertThat(context).hasFailed()
                .getFailure().rootCause().hasMessageContaining("set ddk.event.inbox.enabled=true"));
    }

    static class OrderConsumer implements IntegrationEventConsumer<String> {

        private final boolean idempotent;

        OrderConsumer(boolean idempotent) {
            this.idempotent = idempotent;
        }

        @Override
        public String group() {
            return "billing";
        }

        @Override
        public String source() {
            return "orders:paid";
        }

        @Override
        public Class<String> payloadType() {
            return String.class;
        }

        @Override
        public void handle(ReceivedEvent<String> event) {
        }

        @Override
        public boolean idempotent() {
            return idempotent;
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class Consumers {

        @Bean
        OrderConsumer orderConsumer() {
            return new OrderConsumer(false);
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class IdempotentConsumers {

        @Bean
        OrderConsumer orderConsumer() {
            return new OrderConsumer(true);
        }
    }
}
