package com.ddk.concurrency.starter.config;

import com.ddk.concurrency.starter.AggregateLocks;
import com.ddk.concurrency.starter.internal.ConcurrencyAdvisingPostProcessor;
import com.ddk.concurrency.starter.internal.ConcurrencyInterceptor;
import com.ddk.concurrency.starter.internal.RequestRegistry;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Role;
import org.springframework.core.env.Environment;

import java.time.Duration;

/**
 * 并发控制自动配置：聚合锁与防重复提交。
 * <p>
 * 锁与请求登记只在容器里有 {@code RedissonClient} 时注册；拦截始终注册，没有客户端时带注解的方法直接失败，而不是悄悄地不加锁。
 *
 * @author Elijah Du
 */
@AutoConfiguration(after = DdkRedissonClientAutoConfiguration.class, afterName = {
        "org.redisson.spring.starter.RedissonAutoConfigurationV2",
        "org.redisson.spring.starter.RedissonAutoConfigurationV4"
})
@ConditionalOnClass(RedissonClient.class)
@ConditionalOnProperty(prefix = DdkConcurrencyProperties.PREFIX, name = "enabled", matchIfMissing = true)
@EnableConfigurationProperties(DdkConcurrencyProperties.class)
public class DdkConcurrencyAutoConfiguration {

    /**
     * 后置处理器很早就被实例化，所以声明为 static，并且只依赖 {@link Environment}，不连带初始化配置属性类和 Redis 客户端。
     */
    @Bean
    @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
    static ConcurrencyAdvisingPostProcessor ddkConcurrencyAdvisingPostProcessor(ObjectProvider<AggregateLocks> locks,
            ObjectProvider<RequestRegistry> requests, Environment environment) {
        Duration ttl = environment.getProperty(DdkConcurrencyProperties.PREFIX + ".idempotent.ttl", Duration.class, Duration.ofMinutes(10));
        Duration waitTime = environment.getProperty(DdkConcurrencyProperties.PREFIX + ".lock.wait-time", Duration.class, Duration.ofSeconds(3));
        Duration leaseTime = environment.getProperty(DdkConcurrencyProperties.PREFIX + ".lock.lease-time", Duration.class);
        return new ConcurrencyAdvisingPostProcessor(new ConcurrencyInterceptor(locks, requests, ttl, waitTime, leaseTime));
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnBean(RedissonClient.class)
    static class ConcurrencyConfiguration {

        @Bean
        @ConditionalOnMissingBean
        AggregateLocks aggregateLocks(RedissonClient redisson, DdkConcurrencyProperties properties) {
            DdkConcurrencyProperties.Lock lock = properties.getLock();
            return new AggregateLocks(redisson, properties.getKeyPrefix(), lock.getWaitTime(), lock.getLeaseTime());
        }

        @Bean
        RequestRegistry ddkRequestRegistry(RedissonClient redisson, DdkConcurrencyProperties properties) {
            return new RequestRegistry(redisson, properties.getKeyPrefix());
        }
    }
}
