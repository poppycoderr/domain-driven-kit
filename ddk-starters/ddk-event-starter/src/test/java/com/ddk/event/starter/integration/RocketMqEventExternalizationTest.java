package com.ddk.event.starter.integration;

import com.ddk.core.domain.AggregateRoot;
import com.ddk.core.domain.DomainEvent;
import com.ddk.core.domain.DomainEventPublisher;
import com.ddk.core.domain.Identifier;
import com.ddk.core.domain.IntegrationEvent;
import com.github.dockerjava.api.model.ExposedPort;
import com.github.dockerjava.api.model.Ports;
import org.apache.rocketmq.client.consumer.DefaultMQPushConsumer;
import org.apache.rocketmq.client.consumer.listener.ConsumeConcurrentlyStatus;
import org.apache.rocketmq.client.consumer.listener.MessageListenerConcurrently;
import org.apache.rocketmq.common.consumer.ConsumeFromWhere;
import org.apache.rocketmq.common.message.MessageExt;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.io.IOException;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * 在真实的 RocketMQ 上验证：事件在事务提交后投递，带契约头，同一个 key 落在同一个队列。
 * <p>
 * broker 会把自己的地址登记到 NameServer，客户端按这个地址直连，所以 broker 端口在宿主机上要与容器内一致。
 */
@Testcontainers
@SpringBootTest(classes = RocketMqEventExternalizationTest.TestApplication.class, properties = {
        "spring.datasource.url=jdbc:h2:mem:rocketmq-events;DB_CLOSE_DELAY=-1",
        "spring.modulith.events.jdbc.schema-initialization.enabled=true",
        "spring.autoconfigure.exclude=org.springframework.modulith.events.messaging.SpringMessagingEventExternalizerConfiguration"
})
class RocketMqEventExternalizationTest {

    private static final String TOPIC = "order-events";

    private static final int BROKER_PORT = freePort();

    @Container
    static final GenericContainer<?> ROCKETMQ = new GenericContainer<>(DockerImageName.parse("apache/rocketmq:5.5.0"))
            .withExposedPorts(9876)
            .withEnv("JAVA_OPT_EXT", "-Xms256m -Xmx256m -Xmn128m")
            .withCreateContainerCmdModifier(cmd -> {
                Ports ports = cmd.getHostConfig().getPortBindings();
                ports.bind(ExposedPort.tcp(BROKER_PORT), Ports.Binding.bindPort(BROKER_PORT));
                cmd.getHostConfig().withPortBindings(ports);
            })
            .withCommand("sh", "-c", String.join(" && ",
                    "printf 'brokerClusterName=DefaultCluster\\nbrokerName=broker-a\\nbrokerId=0\\nbrokerIP1=127.0.0.1\\nlistenPort=%s\\n"
                            + "autoCreateTopicEnable=false\\n' " + BROKER_PORT + " > /tmp/broker.conf",
                    "(./mqnamesrv &)",
                    "./mqbroker -n localhost:9876 -c /tmp/broker.conf"))
            .waitingFor(Wait.forLogMessage(".*The broker.*boot success.*", 1).withStartupTimeout(Duration.ofMinutes(2)));

    private static final List<MessageExt> RECEIVED = new CopyOnWriteArrayList<>();

    private static DefaultMQPushConsumer consumer;

    @Autowired
    private DomainEventPublisher publisher;

    @Autowired
    private TransactionTemplate transaction;

    @Autowired
    private JdbcTemplate jdbc;

    @DynamicPropertySource
    static void rocketmq(DynamicPropertyRegistry registry) {
        registry.add("ddk.event.rocketmq.name-server", RocketMqEventExternalizationTest::nameServer);
    }

    @BeforeAll
    static void subscribe() throws Exception {
        // 直接对 broker 建 topic：按集群建要先等 broker 登记到 NameServer，否则 mqadmin 什么也不做却照样返回 0
        var created = ROCKETMQ.execInContainer("./mqadmin", "updateTopic", "-b", "127.0.0.1:" + BROKER_PORT, "-t", TOPIC, "-r", "4", "-w", "4");
        assertThat(created.getStdout()).as(created.getStderr()).contains("success");

        consumer = new DefaultMQPushConsumer("order-events-test");
        consumer.setNamesrvAddr(nameServer());
        consumer.setConsumeFromWhere(ConsumeFromWhere.CONSUME_FROM_FIRST_OFFSET);
        consumer.subscribe(TOPIC, "*");
        consumer.registerMessageListener((MessageListenerConcurrently) (messages, context) -> {
            RECEIVED.addAll(messages);
            return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
        });
        consumer.start();
        // 建 topic 后 broker 异步把路由登记到 NameServer，等路由可见再开始发送
        await().atMost(Duration.ofSeconds(30)).ignoreExceptions().until(() -> !consumer.fetchSubscribeMessageQueues(TOPIC).isEmpty());
    }

    @AfterAll
    static void unsubscribe() {
        if (consumer != null) {
            consumer.shutdown();
        }
    }

    @Test
    void committedEventsReachRocketMqWithContractHeaders() {
        transaction.executeWithoutResult(status -> publisher.publishEventsOf(Order.settle(OrderId.of(42L), "evt-a")));
        transaction.executeWithoutResult(status -> publisher.publishEventsOf(Order.settle(OrderId.of(42L), "evt-b")));

        await().atMost(Duration.ofSeconds(60)).until(() -> RECEIVED.size() == 2);
        MessageExt first = RECEIVED.stream().filter(m -> "evt-a".equals(m.getUserProperty("ddk-event-id"))).findFirst().orElseThrow();
        MessageExt second = RECEIVED.stream().filter(m -> "evt-b".equals(m.getUserProperty("ddk-event-id"))).findFirst().orElseThrow();

        assertThat(first.getTags()).isEqualTo("settled");
        assertThat(first.getKeys()).isEqualTo("42");
        assertThat(first.getUserProperty("ddk-event-type")).isEqualTo("OrderSettled");
        assertThat(first.getUserProperty("ddk-event-version")).isEqualTo("1");
        assertThat(new String(first.getBody(), StandardCharsets.UTF_8)).contains("\"orderId\":42");
        assertThat(second.getQueueId()).as("events of one aggregate share a queue").isEqualTo(first.getQueueId());

        await().atMost(Duration.ofSeconds(10))
                .until(() -> jdbc.queryForObject("SELECT COUNT(*) FROM event_publication WHERE completion_date IS NULL", Long.class) == 0);
    }

    private static String nameServer() {
        return ROCKETMQ.getHost() + ":" + ROCKETMQ.getMappedPort(9876);
    }

    private static int freePort() {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    static final class OrderId extends Identifier<Long> {

        private OrderId(Long value) {
            super(value);
        }

        static OrderId of(Long value) {
            return new OrderId(value);
        }
    }

    @IntegrationEvent(value = TOPIC + ":settled", key = "orderId", id = "eventId")
    record OrderSettled(
            String eventId,

            OrderId orderId,

            Instant occurredOn
    ) implements DomainEvent {
    }

    static final class Order extends AggregateRoot<OrderId> {

        static Order settle(OrderId id, String eventId) {
            Order order = new Order();
            order.assignId(id);
            order.registerEvent(new OrderSettled(eventId, id, Instant.EPOCH));
            return order;
        }
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    static class TestApplication {
    }
}
