package com.ddk.event.starter.config;

import com.ddk.event.starter.consumer.IntegrationEventDispatcher;
import com.ddk.event.starter.internal.RocketMqEventConsumers;
import org.apache.rocketmq.client.consumer.DefaultMQPushConsumer;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

/**
 * 从 RocketMQ 消费集成事件：应用声明了消费方，并且配置了 {@code ddk.event.rocketmq.name-server} 时生效。
 *
 * @author Elijah Du
 */
@AutoConfiguration(after = DdkEventConsumerAutoConfiguration.class)
@ConditionalOnClass(DefaultMQPushConsumer.class)
@ConditionalOnProperty(prefix = DdkEventProperties.PREFIX, name = "enabled", matchIfMissing = true)
public class DdkRocketMqEventConsumerAutoConfiguration {

    @Bean
    @ConditionalOnBean(IntegrationEventDispatcher.class)
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = DdkEventProperties.PREFIX + ".rocketmq", name = "name-server")
    @ConditionalOnProperty(prefix = DdkEventProperties.PREFIX + ".rocketmq.consumer", name = "enabled", havingValue = "true", matchIfMissing = true)
    RocketMqEventConsumers ddkRocketMqEventConsumers(IntegrationEventDispatcher dispatcher, DdkEventProperties properties) {
        DdkEventProperties.RocketMq rocketmq = properties.getRocketmq();
        String nameServer = rocketmq.getNameServer();
        if (nameServer == null) {
            throw new IllegalStateException("ddk.event.rocketmq.name-server must not be empty");
        }
        return new RocketMqEventConsumers(dispatcher, nameServer, rocketmq.getConsumer().isOrderly());
    }
}
