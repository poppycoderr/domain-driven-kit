package com.ddk.job.starter.config;

import com.ddk.job.starter.internal.LocalLockProvider;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.provider.redis.spring.RedisLockProvider;
import net.javacrumbs.shedlock.spring.annotation.EnableSchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 定时任务自动配置。
 * <p>
 * 开启 Spring 的 {@code @Scheduled}，并接入 ShedLock：标了 {@code @SchedulerLock} 的任务执行前先在共享存储里抢锁，
 * 多个实例里只有抢到锁的那个执行。锁默认放在 Redis 里，应用声明了自己的 {@link LockProvider} 时以应用为准；
 * 没有 Redis 的单实例环境可以用 {@code ddk.job.lock.store=local} 把锁放在进程内存里。
 * <p>
 * 这不是分布式调度：没有分片、没有失败重试、没有控制台。它只解决「同一个任务不要在每个实例上各跑一遍」。
 *
 * @author Elijah Du
 */
@AutoConfiguration(afterName = "org.springframework.boot.data.redis.autoconfigure.DataRedisAutoConfiguration")
@ConditionalOnClass(EnableSchedulerLock.class)
@ConditionalOnProperty(prefix = DdkJobProperties.PREFIX, name = "enabled", matchIfMissing = true)
@EnableConfigurationProperties(DdkJobProperties.class)
public class DdkJobAutoConfiguration {

    /**
     * 默认的持锁时间来自配置。占位符的默认值与 {@link DdkJobProperties.Lock} 保持一致。
     */
    @Configuration(proxyBeanMethods = false)
    @EnableScheduling
    @EnableSchedulerLock(
            defaultLockAtMostFor = "${ddk.job.lock.at-most-for:10m}",
            defaultLockAtLeastFor = "${ddk.job.lock.at-least-for:0s}")
    static class SchedulingConfiguration {
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass({RedisLockProvider.class, RedisConnectionFactory.class})
    @ConditionalOnProperty(prefix = DdkJobProperties.PREFIX + ".lock", name = "store", havingValue = "redis", matchIfMissing = true)
    static class RedisLockProviderConfiguration {

        @Bean
        @ConditionalOnMissingBean(LockProvider.class)
        @ConditionalOnBean(RedisConnectionFactory.class)
        LockProvider ddkJobLockProvider(RedisConnectionFactory connectionFactory, DdkJobProperties properties, Environment environment) {
            String application = environment.getProperty("spring.application.name", "application");
            return new RedisLockProvider(connectionFactory, application, properties.getLock().getKeyPrefix());
        }
    }

    /**
     * 进程内的锁要显式选择：它不跨实例，不能因为恰好没有 Redis 就悄悄顶上。
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnProperty(prefix = DdkJobProperties.PREFIX + ".lock", name = "store", havingValue = "local")
    static class LocalLockProviderConfiguration {

        private static final Logger log = LoggerFactory.getLogger(LocalLockProviderConfiguration.class);

        @Bean
        @ConditionalOnMissingBean(LockProvider.class)
        LockProvider ddkJobLocalLockProvider() {
            log.warn("Scheduled job locks are kept inside this JVM (ddk.job.lock.store=local). Do not run more than one instance.");
            return new LocalLockProvider();
        }
    }
}
