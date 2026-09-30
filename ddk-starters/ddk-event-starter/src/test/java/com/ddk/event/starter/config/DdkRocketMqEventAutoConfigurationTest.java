package com.ddk.event.starter.config;

import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.modulith.events.support.EventExternalizerModuleListener;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("RocketMQ 投递自动装配")
class DdkRocketMqEventAutoConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(DdkEventAutoConfiguration.class, DdkRocketMqEventAutoConfiguration.class));

    @Test
    @DisplayName("没有 producer 也没有配置 NameServer 时不投递到 RocketMQ")
    void staysOffWithoutProducer() {
        runner.run(context -> assertThat(context)
                .doesNotHaveBean(DefaultMQProducer.class)
                .doesNotHaveBean(EventExternalizerModuleListener.class));
    }

    @Test
    @DisplayName("配置 NameServer 后创建 producer 并注册外发监听器")
    void createsProducerFromProperties() {
        runner.withPropertyValues(
                        "ddk.event.rocketmq.name-server=127.0.0.1:9876",
                        "ddk.event.rocketmq.producer-group=orders",
                        "ddk.event.rocketmq.send-timeout=5s")
                .run(context -> {
                    assertThat(context).hasSingleBean(EventExternalizerModuleListener.class);
                    DefaultMQProducer producer = context.getBean(DefaultMQProducer.class);
                    assertThat(producer.getNamesrvAddr()).isEqualTo("127.0.0.1:9876");
                    assertThat(producer.getProducerGroup()).isEqualTo("orders");
                    assertThat(producer.getSendMsgTimeout()).isEqualTo(5000);
                });
    }

    @Test
    @DisplayName("应用自己的 producer 优先")
    void usesApplicationProducer() {
        runner.withUserConfiguration(ApplicationProducer.class)
                .withPropertyValues("ddk.event.rocketmq.name-server=127.0.0.1:9876")
                .run(context -> {
                    assertThat(context).hasSingleBean(EventExternalizerModuleListener.class);
                    assertThat(context.getBean(DefaultMQProducer.class).getProducerGroup()).isEqualTo("application");
                });
    }

    @Test
    @DisplayName("外发关闭或改用外部 outbox 模式时不注册监听器")
    void backsOffWhenModulithExternalizationIsNotListenerBased() {
        runner.withUserConfiguration(ApplicationProducer.class)
                .withPropertyValues("spring.modulith.events.externalization.mode=outbox")
                .run(context -> assertThat(context).doesNotHaveBean(EventExternalizerModuleListener.class));
        runner.withUserConfiguration(ApplicationProducer.class)
                .withPropertyValues("spring.modulith.events.externalization.enabled=false")
                .run(context -> assertThat(context).doesNotHaveBean(EventExternalizerModuleListener.class));
    }

    @Configuration(proxyBeanMethods = false)
    static class ApplicationProducer {

        @Bean
        DefaultMQProducer applicationProducer() {
            return new DefaultMQProducer("application");
        }
    }
}
