package com.ddk.event.starter.inbox;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 让消息消费方对重复投递免疫：同一个消费者对同一条消息只执行一次处理逻辑。
 * <p>
 * 外发是「至少一次」投递，中间件重投、生产端重发都会让同一条消息到达多次。每条消息先以（消费者，消息 ID）为主键
 * 登记到已处理表，登记成功才执行处理逻辑，主键冲突说明已经处理过，直接跳过。登记与处理逻辑在同一个事务里：
 * 处理失败时登记随之回滚，消息重投后可以再次处理。
 * <p>
 * <b>前提</b>，不满足时会把不同的消息当成重复而悄悄跳过：
 * <ul>
 *     <li><b>消息 ID 跨租户唯一。</b>主键只有消费者和消息 ID，不含租户。两个租户的事件用了同一个 ID（例如各自从 1 开始的业务单号），
 *     后到的那条会被当成重复。发布端用 UUID 一类全局唯一的值作为事件 ID；</li>
 *     <li><b>消息来自可信的来源。</b>租户消息头没有签名，能往主题里写消息的人可以冒用任何 ID 和租户。</li>
 * </ul>
 * 同一条消息只处理一次的保证只限于处理逻辑在同一个事务里的数据库写入，记录还在保留期内；数据库之外的副作用可能重复发生。
 *
 * <pre>{@code
 * @KafkaListener(topics = "user-events")
 * void on(UserRegisteredEvent event, @Header("ddk-event-id") String eventId) {
 *     idempotentConsumer.handle("welcome-mail", eventId, () -> mailService.sendWelcome(event.userId()));
 * }
 * }</pre>
 */
public class IdempotentConsumer {

    /**
     * {@link #purgeOlderThan(Duration)} 每批删除的记录数。
     */
    public static final int DEFAULT_PURGE_BATCH_SIZE = 1000;

    private static final Logger log = LoggerFactory.getLogger(IdempotentConsumer.class);

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
     * 删除早于 {@code age} 的记录，每批 {@value #DEFAULT_PURGE_BATCH_SIZE} 条，见 {@link #purgeOlderThan(Duration, int)}。
     *
     * @return 删除的记录数
     */
    public int purgeOlderThan(Duration age) {
        return purgeOlderThan(age, DEFAULT_PURGE_BATCH_SIZE);
    }

    /**
     * 分批删除早于 {@code age} 的记录。保留时长要超过中间件可能重投的最长时间，否则过期后再到达的重复消息会被再处理一次。
     * <p>
     * 每批先经到期索引取出至多 {@code batchSize} 条过期记录的主键，再按主键删除。不在调用方的事务里时每批各自提交，
     * 积压很多时也不会一条语句长时间锁住大量行；多个实例同时清理也安全，只是有的批次删得少一些。
     *
     * @return 删除的记录数
     * @throws IllegalArgumentException {@code batchSize} 小于 1
     */
    public int purgeOlderThan(Duration age, int batchSize) {
        if (batchSize < 1) {
            throw new IllegalArgumentException("batchSize must be at least 1, was " + batchSize);
        }
        Timestamp cutoff = Timestamp.from(clock.instant().minus(age));
        int purged = 0;
        List<String[]> keys;
        do {
            keys = jdbc.query("SELECT consumer, message_id FROM " + table + " WHERE processed_at < ? LIMIT " + batchSize,
                    (rs, row) -> new String[] {rs.getString(1), rs.getString(2)}, cutoff);
            if (!keys.isEmpty()) {
                purged += deleteExpired(keys, cutoff);
            }
        } while (keys.size() == batchSize);
        return purged;
    }

    private int deleteExpired(List<String[]> keys, Timestamp cutoff) {
        StringBuilder sql = new StringBuilder("DELETE FROM ").append(table).append(" WHERE processed_at < ? AND (consumer, message_id) IN (");
        List<Object> args = new ArrayList<>(keys.size() * 2 + 1);
        args.add(cutoff);
        for (int i = 0; i < keys.size(); i++) {
            sql.append(i == 0 ? "(?, ?)" : ", (?, ?)");
            args.add(keys.get(i)[0]);
            args.add(keys.get(i)[1]);
        }
        return jdbc.update(sql.append(')').toString(), args.toArray());
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

    /**
     * 到期索引：清理按 {@code processed_at} 找过期记录，没有它时每轮清理都要扫全表。
     * 不用 {@code IF NOT EXISTS}，MySQL 不支持；是否已经存在由 {@link #prepare} 查元数据判断。
     */
    public String expiryIndex() {
        String name = table.substring(table.lastIndexOf('.') + 1);
        return "CREATE INDEX " + name + "_expiry ON " + table + " (processed_at)";
    }

    /**
     * 把已处理表准备好。{@code initialize} 为 true 时建表，并在没有以 {@code processed_at} 开头的索引时补上 {@link #expiryIndex}；
     * 为 false 时不改表结构，表存在却没有到期索引时记录一条警告，它只影响清理的效率。
     * <p>
     * 两个实例同时启动时可能都去建索引，后一个失败；这时再查一次，索引已经在了就不算失败。
     */
    public void prepare(boolean initialize) {
        if (initialize) {
            jdbc.execute(schema());
        }
        if (columns().isEmpty() || leadingIndexColumns().contains("processed_at")) {
            return;
        }
        if (!initialize) {
            log.warn("Processed message table {} has no index on processed_at, so every purge scans the whole table. Add it with: {}",
                    table, expiryIndex());
            return;
        }
        try {
            jdbc.execute(expiryIndex());
        } catch (DataAccessException e) {
            if (!leadingIndexColumns().contains("processed_at")) {
                throw e;
            }
        }
    }

    private Set<String> columns() {
        return metadata((metaData, schema, name) -> metaData.getColumns(null, schema, name, null), false);
    }

    private Set<String> leadingIndexColumns() {
        return metadata((metaData, schema, name) -> metaData.getIndexInfo(null, schema, name, false, true), true);
    }

    /**
     * 按表名原样、大写、小写依次查元数据：不同数据库对未加引号的标识符大小写处理不同。
     */
    private Set<String> metadata(MetaDataQuery query, boolean leadingOnly) {
        Set<String> values = jdbc.execute((ConnectionCallback<Set<String>>) connection -> {
            DatabaseMetaData metaData = connection.getMetaData();
            String schema = table.contains(".") ? table.substring(0, table.indexOf('.')) : null;
            String name = table.substring(table.indexOf('.') + 1);
            for (String candidate : new String[] {name, name.toUpperCase(Locale.ROOT), name.toLowerCase(Locale.ROOT)}) {
                Set<String> found = new LinkedHashSet<>();
                try (ResultSet rs = query.run(metaData, schema, candidate)) {
                    while (rs.next()) {
                        String column = rs.getString("COLUMN_NAME");
                        if (column != null && (!leadingOnly || rs.getShort("ORDINAL_POSITION") == 1)) {
                            found.add(column.toLowerCase(Locale.ROOT));
                        }
                    }
                }
                if (!found.isEmpty()) {
                    return found;
                }
            }
            return Set.of();
        });
        return values == null ? Set.of() : values;
    }

    @FunctionalInterface
    private interface MetaDataQuery {

        ResultSet run(DatabaseMetaData metaData, @Nullable String schema, String table) throws java.sql.SQLException;
    }
}
