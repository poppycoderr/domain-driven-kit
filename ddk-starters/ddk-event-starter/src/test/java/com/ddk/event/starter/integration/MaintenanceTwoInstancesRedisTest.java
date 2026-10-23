package com.ddk.event.starter.integration;

import com.ddk.core.domain.DomainEventPublisher;
import com.ddk.event.starter.config.DdkEventProperties;
import com.ddk.event.starter.internal.EventMaintenance;
import com.ddk.event.starter.internal.IntegrationEventRouting;
import com.ddk.event.starter.internal.ShedLockMaintenanceLock;
import com.ddk.test.containers.DdkContainers;
import com.ddk.test.containers.RedisContainer;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.provider.redis.spring.RedisLockProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.modulith.events.CompletedEventPublications;
import org.springframework.modulith.events.FailedEventPublications;
import org.springframework.modulith.events.ResubmissionOptions;
import org.springframework.modulith.events.core.EventPublicationRepository;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * 两个实例同时做事件维护，各自通过 ShedLock 的 Redis 锁提供者连同一个 Redis：一轮维护只在一个实例上执行，失败的发布各重投一次。
 * 外发通道每条消息停 200ms，让两个实例的维护在时间上一定重叠。
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(classes = MaintenanceTwoInstancesRedisTest.TestApplication.class, properties = {
        "spring.datasource.url=jdbc:h2:mem:maintenance-two-instances;DB_CLOSE_DELAY=-1",
        "spring.modulith.events.jdbc.schema-initialization.enabled=true"
})
@DisplayName("两个实例同时做事件维护（真实 Redis 锁）")
class MaintenanceTwoInstancesRedisTest {

    private static final int FAILED = 5;

    @Container
    static final RedisContainer REDIS = DdkContainers.redis();

    @Autowired
    private DomainEventPublisher publisher;

    @Autowired
    private TransactionTemplate transaction;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private EventPublicationRepository repository;

    @Autowired
    private FailedEventPublications failed;

    @Autowired
    private CompletedEventPublications completed;

    @Autowired
    private SlowChannel orderEvents;

    @Test
    @DisplayName("同时开始的两轮维护只有一轮执行；每条失败的发布恰好重投一次")
    void oneInstanceMaintainsAtATime() throws Exception {
        for (long id = 1; id <= FAILED; id++) {
            long orderId = id;
            transaction.executeWithoutResult(status -> publisher.publishEventsOf(IntegrationEventExternalizationTest.Order.pay(
                    IntegrationEventExternalizationTest.OrderId.of(orderId))));
        }
        await().atMost(Duration.ofSeconds(10)).until(() -> completedCount() == FAILED);
        for (UUID id : jdbc.queryForList("SELECT id FROM event_publication", UUID.class)) {
            repository.markFailed(id);
        }
        orderEvents.sent.clear();
        orderEvents.delay = Duration.ofMillis(200);
        Thread.sleep(50);

        Instance first = new Instance(failed, completed);
        Instance second = new Instance(failed, completed);
        CountDownLatch start = new CountDownLatch(1);
        CompletableFuture<Void> a = CompletableFuture.runAsync(() -> first.runAfter(start));
        CompletableFuture<Void> b = CompletableFuture.runAsync(() -> second.runAfter(start));
        start.countDown();
        CompletableFuture.allOf(a, b).get(30, TimeUnit.SECONDS);

        assertThat(first.rounds.get() + second.rounds.get()).as("rounds that actually ran").isEqualTo(1);
        // Modulith hands resubmitted publications to the externalizer asynchronously
        await().atMost(Duration.ofSeconds(15)).until(() -> completedCount() == FAILED);
        assertThat(orderEvents.sent).as("each failed publication resubmitted once").hasSize(FAILED).doesNotHaveDuplicates();
    }

    private long completedCount() {
        Long count = jdbc.queryForObject("SELECT COUNT(*) FROM event_publication WHERE status = 'COMPLETED'", Long.class);
        return count == null ? 0 : count;
    }

    /**
     * 一个实例：自己的锁提供者和维护对象，数据库和 Redis 与另一个实例共用。记录这个实例真正执行重投的轮数。
     */
    private static final class Instance {

        final AtomicInteger rounds = new AtomicInteger();

        private final EventMaintenance maintenance;

        Instance(FailedEventPublications failed, CompletedEventPublications completed) {
            LettuceConnectionFactory connection = new LettuceConnectionFactory(REDIS.getHost(), REDIS.getPort());
            connection.afterPropertiesSet();
            StaticListableBeanFactory beans = new StaticListableBeanFactory();
            beans.addBean("lockProvider", new RedisLockProvider(connection, "test"));
            DdkEventProperties.Maintenance settings = new DdkEventProperties.Maintenance();
            settings.setResubmitMinAge(Duration.ofMillis(1));
            FailedEventPublications counting = new FailedEventPublications() {

                @Override
                public void resubmit(ResubmissionOptions options) {
                    rounds.incrementAndGet();
                    failed.resubmit(options);
                }
            };
            maintenance = new EventMaintenance(counting, completed, null, settings,
                    new ShedLockMaintenanceLock(beans.getBeanProvider(LockProvider.class), settings.getInterval()));
        }

        void runAfter(CountDownLatch start) {
            try {
                start.await(10, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            maintenance.runOnce();
        }
    }

    /**
     * 外发通道：记录发出的事件标识，可以让每条消息停一会儿。
     */
    static final class SlowChannel implements MessageChannel {

        final List<String> sent = new CopyOnWriteArrayList<>();

        volatile Duration delay = Duration.ZERO;

        @Override
        public boolean send(Message<?> message, long timeout) {
            try {
                Thread.sleep(delay.toMillis());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            sent.add(String.valueOf(message.getHeaders().get(IntegrationEventRouting.EVENT_ID_HEADER)));
            return true;
        }
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    public static class TestApplication {

        @Bean
        SlowChannel orderEvents() {
            return new SlowChannel();
        }
    }
}
