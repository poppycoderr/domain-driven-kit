package com.ddk.event.starter.config;

import com.ddk.core.jackson.IdentifierJacksonModule;
import com.ddk.event.starter.consumer.IntegrationEventConsumer;
import com.ddk.event.starter.consumer.IntegrationEventDispatcher;
import com.ddk.event.starter.inbox.IdempotentConsumer;
import com.ddk.event.starter.internal.EventObservations;
import com.ddk.event.starter.internal.IntegrationEventRouting;
import com.ddk.event.starter.internal.LocalEventDelivery;
import io.micrometer.observation.ObservationRegistry;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;

/**
 * 集成事件的消费端：应用里声明了 {@link IntegrationEventConsumer} 时注册分发器；显式开启后注册进程内转发。
 * 从 RocketMQ 消费由 {@link DdkRocketMqEventConsumerAutoConfiguration} 负责。
 *
 * @author Elijah Du
 */
@AutoConfiguration(after = DdkEventAutoConfiguration.class)
@ConditionalOnClass(JsonMapper.class)
@ConditionalOnProperty(prefix = DdkEventProperties.PREFIX, name = "enabled", matchIfMissing = true)
public class DdkEventConsumerAutoConfiguration {

    @Bean
    @ConditionalOnBean(IntegrationEventConsumer.class)
    @ConditionalOnMissingBean
    IntegrationEventDispatcher integrationEventDispatcher(List<IntegrationEventConsumer<?>> consumers, ObjectProvider<JsonMapper> mapper,
            ObjectProvider<IdempotentConsumer> idempotentConsumer, ObjectProvider<ObservationRegistry> observationRegistry) {
        return new IntegrationEventDispatcher(consumers, json(mapper), idempotentConsumer.getIfAvailable(),
                observationRegistry.getIfAvailable(() -> ObservationRegistry.NOOP));
    }

    /**
     * 进程内转发只用于本地开发和测试，所以要显式开启。
     */
    @Bean
    @ConditionalOnBean(IntegrationEventDispatcher.class)
    @ConditionalOnProperty(prefix = DdkEventProperties.PREFIX + ".local-delivery", name = "enabled", havingValue = "true")
    LocalEventDelivery ddkLocalEventDelivery(IntegrationEventDispatcher dispatcher, ObjectProvider<JsonMapper> mapper,
            ObjectProvider<ObservationRegistry> observationRegistry) {
        return new LocalEventDelivery(dispatcher, new IntegrationEventRouting(), json(mapper),
                new EventObservations(observationRegistry.getIfAvailable(() -> ObservationRegistry.NOOP)));
    }

    private static JsonMapper json(ObjectProvider<JsonMapper> mapper) {
        return mapper.getIfAvailable(() -> JsonMapper.builder().addModule(new IdentifierJacksonModule()).build());
    }
}
