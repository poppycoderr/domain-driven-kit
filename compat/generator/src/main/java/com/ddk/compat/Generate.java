package com.ddk.compat;

import com.ddk.cache.starter.internal.CacheInvalidationMessage;
import com.ddk.concurrency.starter.AggregateLocks;
import com.ddk.core.domain.DomainEventPublisher;
import com.ddk.core.jackson.IdentifierJacksonModule;
import com.ddk.event.starter.consumer.IntegrationEventDispatcher;
import com.ddk.event.starter.inbox.IdempotentConsumer;
import com.ddk.event.starter.internal.RocketMqEventTransport;
import com.ddk.event.starter.internal.TransportProbe;
import com.ddk.projection.starter.internal.PendingRefreshes;
import com.ddk.redis.starter.serializer.RedisJsonMapper;
import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.apache.rocketmq.common.message.Message;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.expression.BeanFactoryResolver;
import org.springframework.data.redis.serializer.GenericJacksonJsonRedisSerializer;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.modulith.events.EventExternalizationConfiguration;
import org.springframework.modulith.events.RoutingTarget;
import org.springframework.modulith.events.support.BrokerRouting;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Writes data exactly as DDK 0.10.0 produces it, for the compatibility tests of later versions. */
@SpringBootApplication
public class Generate {

    public static void main(String[] args) throws Exception {
        // Timestamps in the fixtures do not depend on where the generator runs
        java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone(ZoneOffset.UTC));
        Path out = Path.of(args[0]);
        Files.createDirectories(out);
        JsonMapper json = JsonMapper.builder().build();
        Clock clock = Clock.fixed(Instant.parse("2026-10-22T08:00:00Z"), ZoneOffset.UTC);

        ConfigurableApplicationContext context = SpringApplication.run(Generate.class,
                "--spring.main.web-application-type=none",
                "--spring.datasource.url=jdbc:h2:mem:outbox;DB_CLOSE_DELAY=-1",
                "--spring.modulith.events.jdbc.schema-initialization.enabled=true",
                "--ddk.event.rocketmq.name-server=127.0.0.1:9",
                "--spring.autoconfigure.exclude=com.ddk.concurrency.starter.config.DdkConcurrencyAutoConfiguration,"
                        + "com.ddk.concurrency.starter.config.DdkRedissonClientAutoConfiguration,"
                        + "com.ddk.cache.starter.config.DdkCacheAutoConfiguration,"
                        + "com.ddk.projection.starter.config.DdkProjectionAutoConfiguration");

        ShipmentDispatched event = new ShipmentDispatched("evt-7", ShipmentId.of(7L), "tenant-a", "SF", Instant.parse("2026-10-22T08:00:00Z"));

        // Outbox: the row Spring Modulith writes when the event is published inside a transaction
        context.getBean(TransactionTemplate.class).executeWithoutResult(status ->
                context.getBean(DomainEventPublisher.class).publishEventsOf(Shipment.dispatch(ShipmentId.of(7L))));
        JdbcTemplate outbox = context.getBean(JdbcTemplate.class);
        Map<String, Object> publication = null;
        for (int i = 0; i < 50 && publication == null; i++) {
            List<Map<String, Object>> rows = outbox.queryForList("SELECT event_type, listener_id, serialized_event FROM event_publication");
            publication = rows.isEmpty() ? null : rows.getFirst();
            Thread.sleep(100);
        }
        write(json, out.resolve("outbox-publication.json"), new TreeMap<>(lower(publication)));

        // The RocketMQ message, as the 0.10.0 transport builds it
        EventExternalizationConfiguration configuration = context.getBean(EventExternalizationConfiguration.class);
        StandardEvaluationContext evaluation = new StandardEvaluationContext();
        evaluation.setBeanResolver(new BeanFactoryResolver(context.getBeanFactory()));
        JsonMapper transportMapper = JsonMapper.builder().addModule(new IdentifierJacksonModule()).build();
        RocketMqEventTransport transport = new RocketMqEventTransport(new DefaultMQProducer("fixture"), configuration, transportMapper, evaluation);
        RoutingTarget target = configuration.determineTarget(event);
        BrokerRouting routing = BrokerRouting.of(target, evaluation);
        Message message = TransportProbe.message(transport, routing.getTarget(event), routing.getKey(event), event);
        String body = new String(message.getBody(), StandardCharsets.UTF_8);
        Map<String, Object> rocketmq = new TreeMap<>();
        rocketmq.put("topic", message.getTopic());
        rocketmq.put("tags", message.getTags());
        rocketmq.put("keys", message.getKeys());
        rocketmq.put("properties", new TreeMap<>(message.getProperties()));
        rocketmq.put("body", body);
        write(json, out.resolve("rocketmq-message.json"), rocketmq);

        // The processed message table: schema and the row 0.10.0 writes for an idempotent consumer
        DriverManagerDataSource inboxSource = new DriverManagerDataSource("jdbc:h2:mem:inbox;DB_CLOSE_DELAY=-1", "sa", "");
        JdbcTemplate inboxJdbc = new JdbcTemplate(inboxSource);
        IdempotentConsumer inbox = new IdempotentConsumer(inboxJdbc, new DataSourceTransactionManager(inboxSource), "ddk_processed_message", clock);
        inboxJdbc.execute(inbox.schema());
        ShipmentTracker tracker = new ShipmentTracker();
        IntegrationEventDispatcher dispatcher = new IntegrationEventDispatcher(List.of(tracker), transportMapper, inbox);
        int handled = dispatcher.dispatch("billing", message.getTopic(), message.getTags(), message.getProperties(), body);
        Map<String, Object> processed = new TreeMap<>();
        processed.put("schema", inbox.schema());
        processed.put("handled", handled);
        processed.put("rows", inboxJdbc.queryForList("SELECT * FROM ddk_processed_message").stream().map(Generate::lower).toList());
        write(json, out.resolve("processed-message-table.json"), processed);

        // The projection mark table: schema, due index and one mark
        DriverManagerDataSource markSource = new DriverManagerDataSource("jdbc:h2:mem:marks;DB_CLOSE_DELAY=-1", "sa", "");
        JdbcTemplate markJdbc = new JdbcTemplate(markSource);
        PendingRefreshes marks = new PendingRefreshes(markJdbc, new DataSourceTransactionManager(markSource), "ddk_projection_pending", clock);
        marks.prepare(true);
        marks.mark("catalog", "tenant-a", "42");
        Map<String, Object> pending = new TreeMap<>();
        pending.put("schema", marks.schema());
        pending.put("dueIndex", marks.dueIndex());
        pending.put("rows", markJdbc.queryForList("SELECT * FROM ddk_projection_pending").stream().map(Generate::lower).toList());
        write(json, out.resolve("projection-pending-table.json"), pending);
        Map<String, Object> row = markJdbc.queryForList("SELECT * FROM ddk_projection_pending").getFirst();
        Files.writeString(out.resolve("projection-pending-table.sql"), marks.schema() + ";\n" + marks.dueIndex() + ";\n"
                + "INSERT INTO ddk_projection_pending (projection, tenant_id, entity_id, version, generation, claim_token, claimed_until, attempts, "
                + "available_at, last_error) VALUES ('" + row.get("PROJECTION") + "', '" + row.get("TENANT_ID") + "', '" + row.get("ENTITY_ID") + "', "
                + row.get("VERSION") + ", '" + row.get("GENERATION") + "', NULL, NULL, " + row.get("ATTEMPTS") + ", TIMESTAMP '2026-10-22 08:00:00', NULL);\n");

        // Redis: a cached value through the Redis starter's serializer, the cache invalidation messages, the lock key
        GenericJacksonJsonRedisSerializer serializer = new GenericJacksonJsonRedisSerializer(RedisJsonMapper.create(List.of("com.ddk.compat")));
        CachedProduct product = new CachedProduct("sku-1", new BigDecimal("19.90"), Instant.parse("2026-10-22T08:00:00Z"), List.of("new", "sale"), null);
        Map<String, Object> redis = new TreeMap<>();
        redis.put("cachedValue", new String(serializer.serialize(product), StandardCharsets.UTF_8));
        redis.put("evictMessage", json.writeValueAsString(new CacheInvalidationMessage("instance-a", "products", "sku-1")));
        redis.put("clearMessage", json.writeValueAsString(new CacheInvalidationMessage("instance-a", "products", null)));
        redis.put("lockKey", new AggregateLocks(null, "ddk:", Duration.ofSeconds(3), null).lockName("order", ShipmentId.of(42L)));
        write(json, out.resolve("redis.json"), redis);
        Files.writeString(out.resolve("lock-key.txt"), (String) redis.get("lockKey") + "\n");

        context.close();
        System.out.println("written to " + out);
    }

    private static Map<String, Object> lower(Map<String, Object> row) {
        Map<String, Object> result = new TreeMap<>();
        row.forEach((name, value) -> result.put(name.toLowerCase(), value == null ? null : String.valueOf(value)));
        return result;
    }

    private static void write(JsonMapper json, Path file, Object value) throws Exception {
        Files.writeString(file, json.writerWithDefaultPrettyPrinter().writeValueAsString(value) + "\n");
    }
}
