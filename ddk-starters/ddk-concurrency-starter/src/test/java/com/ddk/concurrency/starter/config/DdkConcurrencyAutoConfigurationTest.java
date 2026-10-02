package com.ddk.concurrency.starter.config;

import com.ddk.concurrency.starter.AggregateLock;
import com.ddk.concurrency.starter.AggregateLocks;
import com.ddk.concurrency.starter.Idempotent;
import com.ddk.concurrency.starter.RateLimit;
import com.ddk.concurrency.starter.internal.ConcurrencyAdvisingPostProcessor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.redisson.api.RedissonClient;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

@DisplayName("并发控制 starter 自动装配")
class DdkConcurrencyAutoConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(DdkConcurrencyAutoConfiguration.class))
            .withUserConfiguration(Services.class);

    @Test
    @DisplayName("没有 Redis 连接信息也没有 RedissonClient 时不创建客户端，带注解的方法直接失败而不是悄悄不加锁")
    void failsLoudlyWithoutRedissonClient() {
        runner.run(context -> {
            assertThat(context).doesNotHaveBean(RedissonClient.class).doesNotHaveBean(AggregateLocks.class);
            OrderService service = context.getBean(OrderService.class);
            assertThatThrownBy(() -> service.cancel(1L))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("@AggregateLock on OrderService.cancel needs a RedissonClient");
            assertThatThrownBy(() -> service.submit("r-1"))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("@Idempotent on OrderService.submit needs a RedissonClient");
            assertThatThrownBy(() -> service.search("q"))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("@RateLimit on OrderService.search needs a RedissonClient");
            assertThat(service.plain()).isEqualTo("plain");
        });
    }

    @Test
    @DisplayName("应用声明的 RedissonClient 优先，并据此注册 AggregateLocks")
    void usesApplicationRedissonClient() {
        runner.withUserConfiguration(ApplicationClient.class).run(context -> {
            assertThat(context).hasSingleBean(RedissonClient.class).hasSingleBean(AggregateLocks.class);
            assertThat(context.getBean(AggregateLocks.class).lockName("order", 42L)).isEqualTo("ddk:lock:order:42");
        });
    }

    @Test
    @DisplayName("key 前缀可配置")
    void keyPrefixIsConfigurable() {
        runner.withUserConfiguration(ApplicationClient.class)
                .withPropertyValues("ddk.concurrency.key-prefix=shop:")
                .run(context -> assertThat(context.getBean(AggregateLocks.class).lockName("order", 42L)).isEqualTo("shop:lock:order:42"));
    }

    @Test
    @DisplayName("关闭后不注册任何 Bean，注解不再生效")
    void disabled() {
        runner.withPropertyValues("ddk.concurrency.enabled=false").run(context -> {
            assertThat(context).doesNotHaveBean(ConcurrencyAdvisingPostProcessor.class);
            assertThat(context.getBean(OrderService.class).cancel(1L)).isEqualTo("cancelled");
        });
    }

    @Configuration(proxyBeanMethods = false)
    static class Services {

        @Bean
        OrderService orderService() {
            return new OrderService();
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class ApplicationClient {

        @Bean
        RedissonClient applicationRedissonClient() {
            return mock(RedissonClient.class);
        }
    }

    static class OrderService {

        @AggregateLock(type = "order", id = "#orderId")
        String cancel(Long orderId) {
            return "cancelled";
        }

        @Idempotent(key = "#requestId")
        String submit(String requestId) {
            return "submitted";
        }

        @RateLimit(limit = 1, period = "1s")
        String search(String keyword) {
            return "found";
        }

        String plain() {
            return "plain";
        }
    }
}
