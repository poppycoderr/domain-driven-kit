package com.ddk.event.starter.inbox;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * 让消息消费方对重复投递免疫：同一个消费者对同一条消息只执行一次处理逻辑。
 * <p>
 * 外发是「至少一次」投递，中间件重投、生产端重发都会让同一条消息到达多次。每条消息先以（消费者，消息 ID）为主键
 * 登记到已处理表，登记成功才执行处理逻辑，主键冲突说明已经处理过，直接跳过。登记与处理逻辑在同一个事务里：
 * 处理失败时登记随之回滚，消息重投后可以再次处理。
 *
 * <pre>{@code
 * @KafkaListener(topics = "user-events")
 * void on(UserRegisteredEvent event, @Header("ddk-event-id") String eventId) {
 *     idempotentConsumer.handle("welcome-mail", eventId, () -> mailService.sendWelcome(event.userId()));
 * }
 * }</pre>
 */
public class IdempotentConsumer {

    private static final Pattern TABLE_NAME = Pattern.compile("[A-Za-z_][A-Za-z0-9_.]*");

    private final JdbcTemplate jdbc;

    private final TransactionTemplate required;

    private final TransactionTemplate nested;

    private final String table;

    private final Clock clock;

    public IdempotentConsumer(JdbcTemplate jdbc, PlatformTransactionManager transactionManager, String table, Clock clock) {
        if (!TABLE_NAME.matcher(table).matches()) {
            throw new IllegalArgumentException("Invalid processed message table name: " + table);
        }
        this.jdbc = jdbc;
        this.required = new TransactionTemplate(transactionManager);
        this.nested = new TransactionTemplate(transactionManager);
        this.nested.setPropagationBehavior(TransactionDefinition.PROPAGATION_NESTED);
        this.table = table;
        this.clock = clock;
    }

    /**
     * 消息没有被这个消费者处理过时执行 {@code handler} 并返回 true；处理过则跳过并返回 false。
     * <p>
     * 在调用方已有的事务里执行，没有事务时新开一个。登记放在嵌套事务（保存点）里：重复消息只回滚到保存点，
     * 不会让 PostgreSQL 这类数据库把整个外层事务标记为失败。
     *
     * @param consumer  消费者名称，同一条消息被多个消费者处理时彼此独立
     * @param messageId 消息的稳定标识，例如消息头 {@code ddk-event-id}
     */
    public boolean handle(String consumer, String messageId, Runnable handler) {
        Objects.requireNonNull(handler, "handler");
        return Boolean.TRUE.equals(required.execute(status -> {
            if (!register(consumer, messageId)) {
                return false;
            }
            handler.run();
            return true;
        }));
    }

    public boolean isProcessed(String consumer, String messageId) {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM " + table + " WHERE consumer = ? AND message_id = ?",
                Integer.class, consumer, messageId);
        return count != null && count > 0;
    }

    /**
     * 删除早于 {@code age} 的记录。保留时长要超过中间件可能重投的最长时间，否则过期后再到达的重复消息会被再处理一次。
     *
     * @return 删除的记录数
     */
    public int purgeOlderThan(Duration age) {
        return jdbc.update("DELETE FROM " + table + " WHERE processed_at < ?", Timestamp.from(clock.instant().minus(age)));
    }

    private boolean register(String consumer, String messageId) {
        Objects.requireNonNull(consumer, "consumer");
        Objects.requireNonNull(messageId, "messageId");
        try {
            nested.executeWithoutResult(status -> jdbc.update(
                    "INSERT INTO " + table + " (consumer, message_id, processed_at) VALUES (?, ?, ?)",
                    consumer, messageId, Timestamp.from(clock.instant())));
            return true;
        } catch (DuplicateKeyException e) {
            return false;
        }
    }

    /**
     * 建表语句。{@code CREATE TABLE IF NOT EXISTS} 适用于 MySQL、PostgreSQL、H2；其他数据库请自行建表并关闭自动初始化。
     */
    public String schema() {
        return "CREATE TABLE IF NOT EXISTS " + table + " ("
                + "consumer VARCHAR(200) NOT NULL, "
                + "message_id VARCHAR(200) NOT NULL, "
                + "processed_at TIMESTAMP NOT NULL, "
                + "PRIMARY KEY (consumer, message_id))";
    }
}
