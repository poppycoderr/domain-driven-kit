package com.ddk.event.starter.config;

import com.ddk.core.domain.DomainEventPublisher;
import com.ddk.event.starter.internal.SpringDomainEventPublisher;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;

/**
 * 领域事件自动配置。
 * <p>
 * 只注册一个 Bean：把 {@code ddk-core} 定义的 {@link DomainEventPublisher} 契约
 * 接到 Spring 的事件总线上。之所以单独成一个 starter 而不是塞进 web 或 mybatis：
 * 领域事件既不是 Web 关注点也不是持久化关注点，非 Web 的消费者应用同样需要它。
 *
 * @author Elijah Du
 */
@AutoConfiguration
@EnableConfigurationProperties(DdkEventProperties.class)
@ConditionalOnProperty(prefix = DdkEventProperties.PREFIX, name = "enabled", matchIfMissing = true)
public class DdkEventAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public DomainEventPublisher domainEventPublisher(ApplicationEventPublisher delegate) {
        return new SpringDomainEventPublisher(delegate);
    }
}
