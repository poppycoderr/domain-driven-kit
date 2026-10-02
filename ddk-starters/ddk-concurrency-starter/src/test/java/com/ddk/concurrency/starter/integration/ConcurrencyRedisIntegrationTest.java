package com.ddk.concurrency.starter.integration;

import com.ddk.test.containers.DdkContainers;
import com.ddk.test.containers.RedisContainer;
import com.ddk.concurrency.starter.AggregateLock;
import com.ddk.concurrency.starter.AggregateLocks;
import com.ddk.concurrency.starter.Idempotent;
import com.ddk.concurrency.starter.RateLimit;
import com.ddk.core.domain.Identifier;
import com.ddk.core.exception.AggregateBusyException;
import com.ddk.core.exception.DuplicateRequestException;
import com.ddk.core.exception.RateLimitedException;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.StringCodec;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

/**
 * 对真实 Redis 验证：锁确实互斥、在事务提交之后才释放，重复请求确实被拒绝、失败后可以重试。
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(classes = ConcurrencyRedisIntegrationTest.TestApplication.class, properties = {
        "spring.datasource.url=jdbc:h2:mem:concurrency;DB_CLOSE_DELAY=-1",
        "ddk.concurrency.key-prefix=it:",
        "ddk.concurrency.lock.wait-time=0"
})
class ConcurrencyRedisIntegrationTest {

    @Container
    static final RedisContainer REDIS = DdkContainers.redis();

    @Autowired
    private OrderService orders;

    @Autowired
    private AggregateLocks locks;

    @Autowired
    private RedissonClient redisson;

    @DynamicPropertySource
    static void redis(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", REDIS::getPort);
    }

    @BeforeEach
    void flush() {
        redisson.getKeys().flushall();
        orders.reset();
    }

    @Test
    @DisplayName("同一个聚合同时只有一个操作在处理，拿不到锁的抛 AggregateBusyException")
    void sameAggregateIsExclusive() throws Exception {
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        CompletableFuture<String> first = CompletableFuture.supplyAsync(() -> orders.cancel(OrderId.of(42L), entered, release));
        assertThat(entered.await(10, TimeUnit.SECONDS)).isTrue();

        assertThat(redisson.getLock("it:lock:order:42").isLocked()).isTrue();
        assertThatThrownBy(() -> orders.cancel(OrderId.of(42L), new CountDownLatch(1), new CountDownLatch(0)))
                .isInstanceOf(AggregateBusyException.class)
                .hasMessageContaining("order");
        assertThat(orders.cancel(OrderId.of(43L), new CountDownLatch(1), new CountDownLatch(0))).isEqualTo("cancelled 43");

        release.countDown();
        assertThat(first.get(10, TimeUnit.SECONDS)).isEqualTo("cancelled 42");
        assertThat(redisson.getLock("it:lock:order:42").isLocked()).isFalse();
        assertThat(orders.cancel(OrderId.of(42L), new CountDownLatch(1), new CountDownLatch(0))).isEqualTo("cancelled 42");
    }

    @Test
    @DisplayName("锁包在事务外面：事务提交时仍然持有锁")
    void lockOutlivesTheTransaction() {
        orders.pay(7L);

        assertThat(orders.transactionActiveInside()).isTrue();
        assertThat(orders.lockedAtCommit()).isTrue();
        assertThat(redisson.getLock("it:lock:order:7").isLocked()).isFalse();
    }

    @Test
    @DisplayName("方法抛异常时锁照常释放，返回 null 的方法照常工作")
    void releasesOnFailureAndAllowsNullResults() {
        assertThatThrownBy(() -> orders.fail(9L)).isInstanceOf(IllegalArgumentException.class).hasMessage("rejected");
        assertThat(redisson.getLock("it:lock:order:9").isLocked()).isFalse();
        assertThat(orders.find(9L)).isNull();
    }

    @Test
    @DisplayName("注解上的等待时间覆盖默认值：短暂占用时等到锁")
    void waitTimeOnTheAnnotationOverridesTheDefault() throws Exception {
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        CompletableFuture<String> first = CompletableFuture.supplyAsync(() -> orders.cancel(OrderId.of(11L), entered, release));
        assertThat(entered.await(10, TimeUnit.SECONDS)).isTrue();
        CompletableFuture.runAsync(release::countDown, CompletableFuture.delayedExecutor(300, TimeUnit.MILLISECONDS));

        assertThat(orders.patient(11L)).isEqualTo("waited");
        assertThat(first.get(10, TimeUnit.SECONDS)).isEqualTo("cancelled 11");
    }

    @Test
    @DisplayName("编程式入口可重入")
    void programmaticLocksAreReentrant() {
        String result = locks.execute("order", 5L, () -> locks.execute("order", OrderId.of(5L), () -> "inner"));

        assertThat(result).isEqualTo("inner");
        assertThat(redisson.getLock("it:lock:order:5").isLocked()).isFalse();
    }

    @Test
    @DisplayName("同一个请求只执行一次，登记带过期时间")
    void duplicateRequestsAreRejected() {
        assertThat(orders.submit("req-1")).isEqualTo(1);
        assertThatThrownBy(() -> orders.submit("req-1")).isInstanceOf(DuplicateRequestException.class);
        assertThat(orders.submit("req-2")).isEqualTo(2);

        long ttl = redisson.getBucket("it:idempotent:OrderService.submit:req-1", StringCodec.INSTANCE).remainTimeToLive();
        assertThat(ttl).isBetween(Duration.ofMinutes(9).toMillis(), Duration.ofMinutes(10).toMillis());
    }

    @Test
    @DisplayName("执行失败时撤销登记，同一个 key 可以重试；作用域与过期时间可在注解上指定")
    void failedRequestsCanBeRetried() {
        assertThatThrownBy(() -> orders.submitOnce("req-9", true)).isInstanceOf(IllegalStateException.class);
        assertThat(orders.submitOnce("req-9", false)).isEqualTo("done");
        assertThatThrownBy(() -> orders.submitOnce("req-9", false)).isInstanceOf(DuplicateRequestException.class);

        long ttl = redisson.getBucket("it:idempotent:checkout:req-9", StringCodec.INSTANCE).remainTimeToLive();
        assertThat(ttl).isBetween(1L, Duration.ofSeconds(30).toMillis());
    }

    @Test
    @DisplayName("每个 key 各有一份额度，超过时抛 RateLimitedException，窗口过后恢复")
    void rateLimitIsPerKey() {
        assertThat(orders.search("alice")).isEqualTo("results for alice");
        assertThat(orders.search("alice")).isEqualTo("results for alice");
        assertThatThrownBy(() -> orders.search("alice")).isInstanceOf(RateLimitedException.class);
        assertThat(orders.search("bob")).isEqualTo("results for bob");

        await().atMost(Duration.ofSeconds(5)).ignoreExceptions().until(() -> orders.search("alice").equals("results for alice"));
        assertThat(redisson.getKeys().getKeysStream().toList()).anyMatch(key -> key.startsWith("it:rate:OrderService.search:2/1000ms:alice"));
    }

    @Test
    @DisplayName("不写 key 时整个用例共用一份额度；被限流的调用不占用请求登记")
    void rateLimitWithoutKeyIsSharedAndComesFirst() {
        assertThat(orders.export("req-1")).isEqualTo("exported");
        assertThatThrownBy(() -> orders.export("req-2")).isInstanceOf(RateLimitedException.class);

        assertThat(redisson.getBucket("it:idempotent:OrderService.export:req-2", StringCodec.INSTANCE).isExists()).isFalse();
        assertThat(redisson.getBucket("it:idempotent:OrderService.export:req-1", StringCodec.INSTANCE).isExists()).isTrue();
    }

    @Test
    @DisplayName("表达式结果为空时报错，指出是哪个方法")
    void emptyKeysAreRejected() {
        assertThatThrownBy(() -> orders.submit(" "))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("OrderService.submit evaluated to an empty key");
    }

    static final class OrderId extends Identifier<Long> {

        private OrderId(Long value) {
            super(value);
        }

        static OrderId of(Long value) {
            return new OrderId(value);
        }
    }

    static class OrderService {

        private final RedissonClient redisson;

        private final AtomicInteger submissions = new AtomicInteger();

        private volatile boolean transactionActiveInside;

        private volatile boolean lockedAtCommit;

        OrderService(RedissonClient redisson) {
            this.redisson = redisson;
        }

        // 测试拿到的是代理对象，状态要通过方法读，直接读字段读到的是代理自己的
        boolean transactionActiveInside() {
            return transactionActiveInside;
        }

        boolean lockedAtCommit() {
            return lockedAtCommit;
        }

        void reset() {
            submissions.set(0);
            transactionActiveInside = false;
            lockedAtCommit = false;
        }

        @AggregateLock(type = "order", id = "#id")
        String cancel(OrderId id, CountDownLatch entered, CountDownLatch release) {
            entered.countDown();
            try {
                release.await(10, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return "cancelled " + id.value();
        }

        @Transactional
        @AggregateLock(type = "order", id = "#id")
        void pay(Long id) {
            transactionActiveInside = TransactionSynchronizationManager.isActualTransactionActive();
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {

                @Override
                public void afterCommit() {
                    lockedAtCommit = redisson.getLock("it:lock:order:" + id).isLocked();
                }
            });
        }

        @AggregateLock(type = "order", id = "#id")
        void fail(Long id) {
            throw new IllegalArgumentException("rejected");
        }

        @AggregateLock(type = "order", id = "#id")
        @Nullable String find(Long id) {
            return null;
        }

        @AggregateLock(type = "order", id = "#id", waitTime = "5s", leaseTime = "30s")
        String patient(Long id) {
            return "waited";
        }

        @RateLimit(limit = 2, period = "1s", key = "#user")
        String search(String user) {
            return "results for " + user;
        }

        @RateLimit(limit = 1, period = "1m", scope = "export")
        @Idempotent(key = "#requestId")
        String export(String requestId) {
            return "exported";
        }

        @Idempotent(key = "#requestId")
        int submit(String requestId) {
            return submissions.incrementAndGet();
        }

        @Idempotent(key = "#requestId", scope = "checkout", ttl = "30s")
        String submitOnce(String requestId, boolean fail) {
            if (fail) {
                throw new IllegalStateException("payment gateway down");
            }
            return "done";
        }
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    static class TestApplication {

        @Bean
        OrderService orderService(RedissonClient redisson) {
            return new OrderService(redisson);
        }
    }
}
