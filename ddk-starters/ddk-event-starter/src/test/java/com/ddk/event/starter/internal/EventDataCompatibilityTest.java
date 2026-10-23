package com.ddk.event.starter.internal;

import com.ddk.compat.ShipmentDispatched;
import com.ddk.compat.ShipmentId;
import com.ddk.compat.ShipmentTracker;
import com.ddk.core.jackson.IdentifierJacksonModule;
import com.ddk.event.starter.consumer.IntegrationEventDispatcher;
import com.ddk.event.starter.consumer.ReceivedEvent;
import com.ddk.event.starter.inbox.IdempotentConsumer;
import com.ddk.event.starter.integration.IntegrationEventExternalizationTest;
import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.apache.rocketmq.common.message.Message;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.expression.BeanFactoryResolver;
import org.springframework.core.io.ClassPathResource;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.modulith.events.EventExternalizationConfiguration;
import org.springframework.modulith.events.support.BrokerRouting;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 事件数据的跨版本兼容：{@code compat/0.10.0} 下的文件由 DDK 0.10.0 实际产生（见仓库的 {@code compat/generator}）。
 * 当前版本要能读它们，也要写出同样的数据，滚动发布期间新旧实例才能互相读对方发出的消息。
 */
@SpringBootTest(classes = IntegrationEventExternalizationTest.TestApplication.class, properties = {
        "spring.datasource.url=jdbc:h2:mem:event-compat;DB_CLOSE_DELAY=-1",
        "spring.modulith.events.jdbc.schema-initialization.enabled=true"
})
@DisplayName("与 0.10.0 的事件数据兼容")
class EventDataCompatibilityTest {

    private static final JsonMapper FIXTURE = JsonMapper.builder().build();

    private static final JsonMapper TRANSPORT = JsonMapper.builder().addModule(new IdentifierJacksonModule()).build();

    private static final ShipmentDispatched EVENT = new ShipmentDispatched("evt-7", ShipmentId.of(7L), "tenant-a", "SF",
            Instant.parse("2026-10-22T08:00:00Z"));

    @Autowired
    private EventExternalizationConfiguration configuration;

    @Autowired
    private ConfigurableListableBeanFactory beanFactory;

    @Test
    @DisplayName("RocketMQ 消息：当前版本发出的主题、标签、key、属性和消息体与 0.10.0 相同")
    void writesTheSameMessage() {
        JsonNode fixture = fixture("rocketmq-message.json");
        Message message = currentMessage();

        assertThat(message.getTopic()).isEqualTo(fixture.get("topic").asString());
        assertThat(message.getTags()).isEqualTo(fixture.get("tags").asString());
        assertThat(message.getKeys()).isEqualTo(fixture.get("keys").asString());
        assertThat(new TreeMap<>(message.getProperties())).isEqualTo(properties(fixture));
        assertThat(new String(message.getBody(), StandardCharsets.UTF_8)).isEqualTo(fixture.get("body").asString());
    }

    @Test
    @DisplayName("RocketMQ 消息：当前版本的消费方读取 0.10.0 发出的消息，事件标识、类型、版本和内容都对")
    void readsTheMessage() {
        JsonNode fixture = fixture("rocketmq-message.json");
        ShipmentTracker tracker = new ShipmentTracker();
        Inbox inbox = inbox();
        inbox.jdbc().execute(inbox.consumer().schema());
        IntegrationEventDispatcher dispatcher = new IntegrationEventDispatcher(List.of(tracker), TRANSPORT, inbox.consumer());

        int handled = dispatcher.dispatch("billing", fixture.get("topic").asString(), fixture.get("tags").asString(), properties(fixture),
                fixture.get("body").asString());

        assertThat(handled).isEqualTo(1);
        assertThat(tracker.received).singleElement().isEqualTo(new ReceivedEvent<>("evt-7", "shipment.dispatched", 2, EVENT));
    }

    @Test
    @DisplayName("已处理消息表：表结构与 0.10.0 相同；0.10.0 登记过的消息，当前版本认得是同一个消费方处理过的，不再处理")
    void recognizesMessagesProcessedBefore() {
        JsonNode fixture = fixture("processed-message-table.json");
        Inbox inbox = inbox();
        assertThat(inbox.consumer().schema()).isEqualTo(fixture.get("schema").asString());
        inbox.jdbc().execute(fixture.get("schema").asString());
        for (JsonNode row : fixture.get("rows")) {
            inbox.jdbc().update("INSERT INTO ddk_processed_message (consumer, message_id, processed_at) VALUES (?, ?, ?)",
                    row.get("consumer").asString(), row.get("message_id").asString(), Timestamp.valueOf(row.get("processed_at").asString()));
        }
        JsonNode message = fixture("rocketmq-message.json");
        ShipmentTracker tracker = new ShipmentTracker();

        int handled = new IntegrationEventDispatcher(List.of(tracker), TRANSPORT, inbox.consumer())
                .dispatch("billing", message.get("topic").asString(), message.get("tags").asString(), properties(message), message.get("body").asString());

        assertThat(handled).as("acknowledged as a duplicate").isEqualTo(1);
        assertThat(tracker.received).as("not handled again").isEmpty();
    }

    @Test
    @DisplayName("Outbox：0.10.0 记录的事件类型是类的全名，序列化内容当前版本读得回来，当前版本序列化出的也相同")
    void readsAndWritesTheOutboxPayload() {
        JsonNode fixture = fixture("outbox-publication.json");

        assertThat(fixture.get("event_type").asString()).isEqualTo(ShipmentDispatched.class.getName());
        assertThat(TRANSPORT.readValue(fixture.get("serialized_event").asString(), ShipmentDispatched.class)).isEqualTo(EVENT);
        assertThat(TRANSPORT.writeValueAsString(EVENT)).isEqualTo(fixture.get("serialized_event").asString());
    }

    private Message currentMessage() {
        StandardEvaluationContext evaluation = new StandardEvaluationContext();
        evaluation.setBeanResolver(new BeanFactoryResolver(beanFactory));
        RocketMqEventTransport transport = new RocketMqEventTransport(new DefaultMQProducer("compat"), configuration, TRANSPORT, evaluation);
        BrokerRouting routing = BrokerRouting.of(configuration.determineTarget(EVENT), evaluation);
        return transport.message(routing.getTarget(EVENT), routing.getKey(EVENT), EVENT);
    }

    private static Inbox inbox() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource("jdbc:h2:mem:event-compat-inbox-" + System.nanoTime() + ";DB_CLOSE_DELAY=-1",
                "sa", "");
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        return new Inbox(jdbc, new IdempotentConsumer(jdbc, new DataSourceTransactionManager(dataSource), "ddk_processed_message",
                Clock.fixed(Instant.parse("2026-10-23T08:00:00Z"), ZoneOffset.UTC)));
    }

    private record Inbox(
            JdbcTemplate jdbc,
            IdempotentConsumer consumer
    ) {
    }

    private static Map<String, String> properties(JsonNode fixture) {
        Map<String, String> properties = new TreeMap<>();
        fixture.get("properties").properties().forEach(entry -> properties.put(entry.getKey(), entry.getValue().asString()));
        return properties;
    }

    private static JsonNode fixture(String name) {
        try (var in = new ClassPathResource("compat/0.10.0/" + name).getInputStream()) {
            return FIXTURE.readTree(in);
        } catch (java.io.IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
