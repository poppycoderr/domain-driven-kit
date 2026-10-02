package com.ddk.test.containers;

import org.apache.rocketmq.client.consumer.DefaultMQPushConsumer;
import org.apache.rocketmq.client.consumer.listener.ConsumeConcurrentlyStatus;
import org.apache.rocketmq.client.consumer.listener.MessageListenerConcurrently;
import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.apache.rocketmq.client.producer.SendStatus;
import org.apache.rocketmq.common.consumer.ConsumeFromWhere;
import org.apache.rocketmq.common.message.Message;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

/**
 * 预置的容器要真的能用：从宿主机连得上，行为与说明一致。
 */
@Testcontainers(disabledWithoutDocker = true)
@DisplayName("容器预置")
class DdkContainersTest {

    @Container
    static final RedisContainer REDIS = DdkContainers.redis();

    @Container
    static final MySQLContainer MYSQL = DdkContainers.mysql();

    @Container
    static final RocketMqContainer ROCKETMQ = DdkContainers.rocketmq();

    @Test
    @DisplayName("Redis：端口已映射，服务可用")
    void redisIsReachable() throws Exception {
        assertThat(REDIS.getPort()).isPositive();
        assertThat(REDIS.execInContainer("redis-cli", "ping").getStdout()).contains("PONG");
    }

    @Test
    @DisplayName("MySQL：字符集是 utf8mb4")
    void mysqlUsesUtf8mb4() throws Exception {
        try (Connection connection = DriverManager.getConnection(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword());
                ResultSet result = connection.createStatement().executeQuery("SELECT @@character_set_server, VERSION()")) {
            assertThat(result.next()).isTrue();
            assertThat(result.getString(1)).isEqualTo("utf8mb4");
            assertThat(result.getString(2)).startsWith("8.4");
        }
    }

    @Test
    @DisplayName("RocketMQ：建 topic 后，宿主机上的 producer 与 consumer 能直连 broker 收发消息")
    void rocketmqSendsAndReceives() throws Exception {
        ROCKETMQ.createTopic("preset-test", 2);
        List<String> received = new CopyOnWriteArrayList<>();

        DefaultMQProducer producer = new DefaultMQProducer("preset-producer");
        producer.setNamesrvAddr(ROCKETMQ.getNameServer());
        DefaultMQPushConsumer consumer = new DefaultMQPushConsumer("preset-consumer");
        consumer.setNamesrvAddr(ROCKETMQ.getNameServer());
        consumer.setConsumeFromWhere(ConsumeFromWhere.CONSUME_FROM_FIRST_OFFSET);
        consumer.subscribe("preset-test", "*");
        consumer.registerMessageListener((MessageListenerConcurrently) (messages, context) -> {
            messages.forEach(message -> received.add(new String(message.getBody(), StandardCharsets.UTF_8)));
            return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
        });
        try {
            producer.start();
            consumer.start();

            assertThat(producer.send(new Message("preset-test", "hello".getBytes(StandardCharsets.UTF_8))).getSendStatus())
                    .isEqualTo(SendStatus.SEND_OK);
            await().atMost(java.time.Duration.ofSeconds(60)).until(() -> received.contains("hello"));
        } finally {
            producer.shutdown();
            consumer.shutdown();
        }
    }

    @Test
    @DisplayName("RocketMQ：topic 没有自动创建，发到不存在的 topic 会失败")
    void rocketmqDoesNotAutoCreateTopics() throws Exception {
        DefaultMQProducer producer = new DefaultMQProducer("preset-producer-strict");
        producer.setNamesrvAddr(ROCKETMQ.getNameServer());
        try {
            producer.start();
            assertThatThrownBy(() -> producer.send(new Message("never-created", "x".getBytes(StandardCharsets.UTF_8))))
                    .hasMessageContaining("No route info");
        } finally {
            producer.shutdown();
        }
    }
}
