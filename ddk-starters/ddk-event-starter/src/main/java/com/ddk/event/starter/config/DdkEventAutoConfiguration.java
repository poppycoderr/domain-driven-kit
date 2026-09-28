package com.ddk.event.starter.config;

import com.ddk.core.domain.DomainEventPublisher;
import com.ddk.core.jackson.IdentifierJacksonModule;
import com.ddk.event.starter.internal.SpringDomainEventPublisher;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.JacksonModule;

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

    /**
     * 事件被序列化时（例如 Spring Modulith 的事件发布记录），事件里的类型化标识要写成原始值才能读回来。
     * Spring Boot 会把容器里的 Jackson 模块注册到它构建的 {@code JsonMapper} 上。
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(JacksonModule.class)
    static class IdentifierJsonConfiguration {

        @Bean
        @ConditionalOnMissingBean
        IdentifierJacksonModule identifierJacksonModule() {
            return new IdentifierJacksonModule();
        }
    }
}
