package com.ddk.event.starter.config;

import com.ddk.core.jackson.IdentifierJacksonModule;
import com.ddk.event.starter.internal.RocketMqEventTransport;
import org.apache.rocketmq.client.exception.MQClientException;
import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.expression.BeanFactoryResolver;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.modulith.events.EventExternalizationConfiguration;
import org.springframework.modulith.events.support.EventExternalizerModuleListener;
import tools.jackson.databind.json.JsonMapper;

/**
 * 经 RocketMQ 投递集成事件。
 * <p>
 * Spring Modulith 为 Kafka、AMQP、JMS 和 Spring Messaging 提供了外发模块，没有 RocketMQ。这里按同样的方式注册一个外发监听器，
 * 事件仍先写进事件发布记录，提交后再发送，失败的记录可以重投。producer 由应用提供（例如 rocketmq-spring 注册的那个），
 * 或者配置 {@code ddk.event.rocketmq.name-server} 让 DDK 创建。
 */
@AutoConfiguration(after = DdkEventAutoConfiguration.class, afterName = "org.apache.rocketmq.spring.autoconfigure.RocketMQAutoConfiguration")
@ConditionalOnClass({DefaultMQProducer.class, EventExternalizerModuleListener.class, JsonMapper.class})
@ConditionalOnProperty(prefix = DdkEventProperties.PREFIX, name = "enabled", matchIfMissing = true)
@ConditionalOnProperty(name = "spring.modulith.events.externalization.enabled", havingValue = "true", matchIfMissing = true)
public class DdkRocketMqEventAutoConfiguration {

    @Bean(destroyMethod = "shutdown")
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = DdkEventProperties.PREFIX + ".rocketmq", name = "name-server")
    DefaultMQProducer ddkRocketMqProducer(DdkEventProperties properties) throws MQClientException {
        DdkEventProperties.RocketMq rocketmq = properties.getRocketmq();
        DefaultMQProducer producer = new DefaultMQProducer(rocketmq.getProducerGroup());
        producer.setNamesrvAddr(rocketmq.getNameServer());
        producer.setSendMsgTimeout(Math.toIntExact(rocketmq.getSendTimeout().toMillis()));
        producer.start();
        return producer;
    }

    /**
     * 只在默认的监听器模式下注册；{@code spring.modulith.events.externalization.mode=outbox} 由 Modulith 的外部 outbox 集成负责。
     */
    @Bean
    @ConditionalOnBean({DefaultMQProducer.class, EventExternalizationConfiguration.class})
    @ConditionalOnProperty(name = "spring.modulith.events.externalization.mode", havingValue = "module-listener", matchIfMissing = true)
    EventExternalizerModuleListener ddkRocketMqEventExternalizer(EventExternalizationConfiguration configuration, DefaultMQProducer producer,
            ObjectProvider<JsonMapper> mapper, BeanFactory beanFactory) {
        StandardEvaluationContext context = new StandardEvaluationContext();
        context.setBeanResolver(new BeanFactoryResolver(beanFactory));
        JsonMapper json = mapper.getIfAvailable(() -> JsonMapper.builder().addModule(new IdentifierJacksonModule()).build());
        return new EventExternalizerModuleListener(configuration, new RocketMqEventTransport(producer, configuration, json, context));
    }
}
