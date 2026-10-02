package com.ddk.job.starter;

import com.ddk.job.starter.config.DdkJobAutoConfiguration;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.data.redis.autoconfigure.DataRedisAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * 用两个应用上下文模拟两个实例，对真实 Redis 验证：加了锁的任务同一轮只在一个实例上执行，不加锁的任务每个实例各执行一次。
 */
@Testcontainers(disabledWithoutDocker = true)
@DisplayName("多实例下的定时任务")
class ScheduledJobLockIntegrationTest {

    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

    private static final AtomicInteger LOCKED_RUNS = new AtomicInteger();

    private static final AtomicInteger UNLOCKED_RUNS = new AtomicInteger();

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(DataRedisAutoConfiguration.class, DdkJobAutoConfiguration.class))
            .withUserConfiguration(Jobs.class)
            .withPropertyValues(
                    "spring.application.name=orders",
                    "spring.data.redis.host=" + REDIS.getHost(),
                    "spring.data.redis.port=" + REDIS.getMappedPort(6379),
                    "ddk.job.lock.at-least-for=30s");

    @BeforeEach
    void reset() {
        LOCKED_RUNS.set(0);
        UNLOCKED_RUNS.set(0);
    }

    @Test
    @DisplayName("加锁的任务只执行一次，锁写在「前缀:应用名:任务名」下")
    void lockedJobRunsOnOneInstanceOnly() {
        runner.run(first -> runner.run(second -> {
            await().atMost(5, TimeUnit.SECONDS).until(() -> UNLOCKED_RUNS.get() >= 2);

            // 两个实例的任务都已触发之后再观察一段时间，确认第二个实例确实没有执行
            await().during(1, TimeUnit.SECONDS).atMost(3, TimeUnit.SECONDS).until(() -> LOCKED_RUNS.get() == 1);
            assertThat(first.getBean(StringRedisTemplate.class).hasKey("ddk:job-lock:orders:closeExpiredOrders")).isTrue();
        }));
    }

    @Configuration(proxyBeanMethods = false)
    static class Jobs {

        @Bean
        OrderJobs orderJobs() {
            return new OrderJobs();
        }

        @Bean
        StringRedisTemplate stringRedisTemplate(org.springframework.data.redis.connection.RedisConnectionFactory factory) {
            return new StringRedisTemplate(factory);
        }
    }

    static class OrderJobs {

        @Scheduled(initialDelay = 200, fixedDelay = 3_600_000)
        @SchedulerLock(name = "closeExpiredOrders")
        public void closeExpiredOrders() {
            LOCKED_RUNS.incrementAndGet();
        }

        @Scheduled(initialDelay = 200, fixedDelay = 3_600_000)
        public void refreshLocalCache() {
            UNLOCKED_RUNS.incrementAndGet();
        }
    }
}
