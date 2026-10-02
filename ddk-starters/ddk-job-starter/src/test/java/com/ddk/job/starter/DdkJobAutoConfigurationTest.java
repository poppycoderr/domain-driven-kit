package com.ddk.job.starter;

import com.ddk.job.starter.config.DdkJobAutoConfiguration;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.provider.redis.spring.RedisLockProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.scheduling.annotation.ScheduledAnnotationBeanPostProcessor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

@DisplayName("定时任务 starter 自动装配")
class DdkJobAutoConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(DdkJobAutoConfiguration.class));

    @Test
    @DisplayName("开启调度；有 Redis 连接时用 Redis 存锁")
    void enablesSchedulingAndUsesRedis() {
        runner.withUserConfiguration(RedisConfiguration.class).run(context -> {
            assertThat(context).hasSingleBean(ScheduledAnnotationBeanPostProcessor.class);
            assertThat(context.getBean(LockProvider.class)).isInstanceOf(RedisLockProvider.class);
        });
    }

    @Test
    @DisplayName("应用声明的 LockProvider 优先")
    void applicationLockProviderWins() {
        runner.withUserConfiguration(RedisConfiguration.class, CustomLockProvider.class)
                .run(context -> assertThat(context.getBean(LockProvider.class)).isSameAs(CustomLockProvider.PROVIDER));
    }

    @Test
    @DisplayName("没有 Redis 连接时不注册 LockProvider")
    void noProviderWithoutRedis() {
        runner.run(context -> assertThat(context).doesNotHaveBean(LockProvider.class));
    }

    @Test
    @DisplayName("关闭后不开启调度")
    void disabled() {
        runner.withUserConfiguration(RedisConfiguration.class).withPropertyValues("ddk.job.enabled=false")
                .run(context -> assertThat(context)
                        .doesNotHaveBean(ScheduledAnnotationBeanPostProcessor.class)
                        .doesNotHaveBean(LockProvider.class));
    }

    @Configuration(proxyBeanMethods = false)
    static class RedisConfiguration {

        @Bean
        RedisConnectionFactory redisConnectionFactory() {
            return mock(RedisConnectionFactory.class);
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class CustomLockProvider {

        static final LockProvider PROVIDER = mock(LockProvider.class);

        @Bean
        LockProvider lockProvider() {
            return PROVIDER;
        }
    }
}
