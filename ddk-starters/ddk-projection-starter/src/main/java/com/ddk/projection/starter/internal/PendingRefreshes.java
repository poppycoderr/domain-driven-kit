package com.ddk.projection.starter.internal;

import org.jspecify.annotations.Nullable;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 待刷新表：每行是「某个读模型的某一条数据需要刷新」。
 * <p>
 * 每行带一个版本号，标记一次加一。刷新完成后按「读到的版本号」删除：刷新期间这条数据又被标记过的话，版本号已经变了，
 * 删除不会命中，这一行留下来再刷新一次。没有这个版本号，刷新期间发生的变更会被一并删掉，读模型就停在旧状态上。
 */
public class PendingRefreshes {

    private static final Pattern TABLE_NAME = Pattern.compile("[A-Za-z_][A-Za-z0-9_.]*");

    private static final int MAX_ERROR_LENGTH = 500;

    private final JdbcTemplate jdbc;

    private final TransactionTemplate nested;

    private final String table;

    private final Clock clock;

    public PendingRefreshes(JdbcTemplate jdbc, PlatformTransactionManager transactionManager, String table, Clock clock) {
        if (!TABLE_NAME.matcher(table).matches()) {
            throw new IllegalArgumentException("Invalid pending refresh table name: " + table);
        }
        this.jdbc = jdbc;
        this.nested = new TransactionTemplate(transactionManager);
        this.nested.setPropagationBehavior(TransactionDefinition.PROPAGATION_NESTED);
        this.table = table;
        this.clock = clock;
    }

    /**
     * 标记一条数据需要刷新，加入调用方的事务。已经有标记时只把版本号加一，并让它立即到期、重新计数。
     * <p>
     * 插入放在嵌套事务（保存点）里：两个事务同时标记同一条数据时后一个会撞上主键，只回滚到保存点再改成更新，
     * 不会让 PostgreSQL 这类数据库把整个外层事务标记为失败。
     */
    public void mark(String projection, String id) {
        if (touch(projection, id) > 0) {
            return;
        }
        try {
            nested.executeWithoutResult(status -> jdbc.update("INSERT INTO " + table
                    + " (projection, entity_id, version, attempts, available_at) VALUES (?, ?, 1, 0, ?)", projection, id, now()));
        } catch (DuplicateKeyException e) {
            touch(projection, id);
        }
    }

    private int touch(String projection, String id) {
        return jdbc.update("UPDATE " + table + " SET version = version + 1, attempts = 0, available_at = ?, last_error = NULL "
                + "WHERE projection = ? AND entity_id = ?", now(), projection, id);
    }

    /**
     * 已经到期的标记，最早到期的在前。
     */
    public List<Pending> due(int limit) {
        return jdbc.query("SELECT projection, entity_id, version, attempts FROM " + table
                        + " WHERE available_at <= ? ORDER BY available_at LIMIT " + limit,
                (rs, row) -> new Pending(rs.getString(1), rs.getString(2), rs.getLong(3), rs.getInt(4)), now());
    }

    /**
     * 刷新成功。刷新期间又被标记过的不会被删除。
     *
     * @return 标记是否已经删除
     */
    public boolean done(Pending pending) {
        return jdbc.update("DELETE FROM " + table + " WHERE projection = ? AND entity_id = ? AND version = ?",
                pending.projection(), pending.id(), pending.version()) > 0;
    }

    /**
     * 刷新失败，过 {@code delay} 之后再试。期间又被标记过的保持立即到期，不受这次失败影响。
     */
    public void failed(Pending pending, Duration delay, @Nullable String error) {
        String message = error == null ? null : error.substring(0, Math.min(error.length(), MAX_ERROR_LENGTH));
        jdbc.update("UPDATE " + table + " SET attempts = attempts + 1, available_at = ?, last_error = ? "
                        + "WHERE projection = ? AND entity_id = ? AND version = ?",
                Timestamp.from(clock.instant().plus(delay)), message, pending.projection(), pending.id(), pending.version());
    }

    public long count(String projection) {
        Long count = jdbc.queryForObject("SELECT COUNT(*) FROM " + table + " WHERE projection = ?", Long.class, projection);
        return count == null ? 0 : count;
    }

    /**
     * 建表语句。{@code CREATE TABLE IF NOT EXISTS} 适用于 MySQL、PostgreSQL、H2；其他数据库请自行建表并关闭自动初始化。
     */
    public String schema() {
        return "CREATE TABLE IF NOT EXISTS " + table + " ("
                + "projection VARCHAR(100) NOT NULL, "
                + "entity_id VARCHAR(200) NOT NULL, "
                + "version BIGINT NOT NULL, "
                + "attempts INT NOT NULL, "
                + "available_at TIMESTAMP NOT NULL, "
                + "last_error VARCHAR(" + MAX_ERROR_LENGTH + "), "
                + "PRIMARY KEY (projection, entity_id))";
    }

    private Timestamp now() {
        return Timestamp.from(clock.instant());
    }

    /**
     * 一条待刷新的标记。
     */
    public record Pending(
            String projection,

            String id,

            long version,

            int attempts
    ) {
    }
}
