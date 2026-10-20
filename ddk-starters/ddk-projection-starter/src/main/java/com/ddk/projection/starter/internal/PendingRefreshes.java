package com.ddk.projection.starter.internal;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.regex.Pattern;

/**
 * 待刷新表：每行是「某个读模型的某一条数据需要刷新」。
 * <p>
 * 每行有两个身份。<b>代次</b>（{@code generation}）在每次标记时换成一个新的随机值，永不复用：它回答「从我开始处理到现在，
 * 这条数据有没有再变过」。<b>领取令牌</b>（{@code claim_token}）在每次领取时生成，带租约：同一时刻只有一个 worker 处理一条标记，
 * 确认时据此判断自己是否仍是领取者。
 * <p>
 * 确认按「代次 + 令牌」删除。删不到说明处理期间又有变化、或者租约过期后被别人接手，这次写进读模型的可能是旧状态，
 * 于是重新标记这条数据：之后一定还有一次刷新，它在这次写入之后才读源状态。读模型因此最终收敛到写模型，
 * 不会出现「读模型是旧的、表里却已经没有标记」。
 * <p>
 * {@code version} 列保留给 0.8 及更早的 worker：每次标记仍然加一，滚动升级期间旧 worker 的确认同样会落空。
 */
public class PendingRefreshes {

    private static final Logger log = LoggerFactory.getLogger(PendingRefreshes.class);

    private static final Pattern TABLE_NAME = Pattern.compile("[A-Za-z_][A-Za-z0-9_.]*");

    private static final int MAX_ERROR_LENGTH = 500;

    /**
     * 0.8 的表结构之后新增的列，以及升级时追加它们的语句。
     */
    private static final List<String[]> ADDED_COLUMNS = List.of(
            new String[] {"generation", "VARCHAR(36)"},
            new String[] {"claim_token", "VARCHAR(36)"},
            new String[] {"claimed_until", "TIMESTAMP"});

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
     * 标记一条数据需要刷新，加入调用方的事务。已经有标记时换一个新代次，并让它立即到期、重新计数；正在处理它的 worker
     * 保留领取，处理完确认时会发现代次变了。
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
                            + " (projection, entity_id, version, generation, attempts, available_at) VALUES (?, ?, 1, ?, 0, ?)",
                    projection, id, newToken(), now()));
        } catch (DuplicateKeyException e) {
            touch(projection, id);
        }
    }

    private int touch(String projection, String id) {
        return jdbc.update("UPDATE " + table + " SET version = version + 1, generation = ?, attempts = 0, available_at = ?, last_error = NULL "
                + "WHERE projection = ? AND entity_id = ?", newToken(), now(), projection, id);
    }

    /**
     * 已经到期、并且没有被别人领取（或领取的租约已过期）的标记，最早到期的在前。
     */
    public List<Pending> due(int limit) {
        Timestamp now = now();
        return jdbc.query("SELECT projection, entity_id, generation, attempts FROM " + table
                        + " WHERE available_at <= ? AND (claim_token IS NULL OR claimed_until < ?) ORDER BY available_at LIMIT " + limit,
                (rs, row) -> new Pending(rs.getString(1), rs.getString(2), rs.getString(3), rs.getInt(4)), now, now);
    }

    /**
     * 领取一条标记，租约为 {@code lease}。标记已经换了代次、或者被别人领取着时返回空。
     */
    public Optional<Claim> claim(Pending pending, Duration lease) {
        String token = newToken();
        Timestamp now = now();
        int claimed = jdbc.update("UPDATE " + table + " SET claim_token = ?, claimed_until = ? "
                        + "WHERE projection = ? AND entity_id = ? AND generation = ? AND (claim_token IS NULL OR claimed_until < ?)",
                token, Timestamp.from(clock.instant().plus(lease)), pending.projection(), pending.id(), pending.generation(), now);
        return claimed > 0 ? Optional.of(new Claim(pending, token)) : Optional.empty();
    }

    /**
     * 刷新完成。只有代次没变、自己仍是领取者时才删除标记；否则释放领取并重新标记，让这条数据再刷新一次。
     *
     * @return 标记是否已经删除
     */
    public boolean done(Claim claim) {
        Pending pending = claim.pending();
        int deleted = jdbc.update("DELETE FROM " + table + " WHERE projection = ? AND entity_id = ? AND generation = ? AND claim_token = ?",
                pending.projection(), pending.id(), pending.generation(), claim.token());
        if (deleted > 0) {
            return true;
        }
        release(claim);
        mark(pending.projection(), pending.id());
        return false;
    }

    /**
     * 刷新失败，过 {@code delay} 之后再试，并释放领取。期间又被标记过的保持立即到期，不受这次失败影响。
     */
    public void failed(Claim claim, Duration delay, @Nullable String error) {
        Pending pending = claim.pending();
        String message = error == null ? null : error.substring(0, Math.min(error.length(), MAX_ERROR_LENGTH));
        int updated = jdbc.update("UPDATE " + table + " SET attempts = attempts + 1, available_at = ?, last_error = ?, "
                        + "claim_token = NULL, claimed_until = NULL WHERE projection = ? AND entity_id = ? AND generation = ? AND claim_token = ?",
                Timestamp.from(clock.instant().plus(delay)), message, pending.projection(), pending.id(), pending.generation(), claim.token());
        if (updated == 0) {
            release(claim);
        }
    }

    private void release(Claim claim) {
        jdbc.update("UPDATE " + table + " SET claim_token = NULL, claimed_until = NULL WHERE projection = ? AND entity_id = ? AND claim_token = ?",
                claim.pending().projection(), claim.pending().id(), claim.token());
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
                + "generation VARCHAR(36), "
                + "claim_token VARCHAR(36), "
                + "claimed_until TIMESTAMP, "
                + "attempts INT NOT NULL, "
                + "available_at TIMESTAMP NOT NULL, "
                + "last_error VARCHAR(" + MAX_ERROR_LENGTH + "), "
                + "PRIMARY KEY (projection, entity_id))";
    }

    /**
     * 到期扫描用的索引：{@link #due} 按 {@code available_at} 过滤并排序，没有它时每次清扫都要扫全表再排序。
     * 不用 {@code IF NOT EXISTS}，MySQL 不支持；是否已经存在由 {@link #prepare} 查元数据判断。
     */
    public String dueIndex() {
        String name = table.substring(table.lastIndexOf('.') + 1);
        return "CREATE INDEX " + name + "_due ON " + table + " (available_at)";
    }

    /**
     * 把待刷新表准备到当前的结构。
     * <p>
     * 表不存在时建表和到期索引。表存在但没有到期索引时：{@code initialize} 为 true 就补上，为 false 只记录警告，它只影响清扫的效率。
     * 表存在但缺少后来新增的列（从 0.8 升级）时：{@code initialize} 为 true 就追加这些列，并给已有的标记补上代次；
     * 为 false 就启动失败，异常里给出需要执行的语句，交给迁移工具。只建不升级的话，旧表会在第一次领取时才报错。
     *
     * @throws IllegalStateException 表缺少列，并且不允许自动升级
     */
    public void prepare(boolean initialize) {
        Set<String> columns = existingColumns();
        if (columns.isEmpty()) {
            if (!initialize) {
                throw new IllegalStateException("Pending refresh table " + table + " does not exist. Create it with: " + schema() + "; " + dueIndex());
            }
            jdbc.execute(schema());
            jdbc.execute(dueIndex());
            return;
        }
        prepareDueIndex(initialize);
        List<String> upgrade = ADDED_COLUMNS.stream().filter(column -> !columns.contains(column[0]))
                .map(column -> "ALTER TABLE " + table + " ADD COLUMN " + column[0] + " " + column[1]).toList();
        if (upgrade.isEmpty()) {
            return;
        }
        String backfill = "UPDATE " + table + " SET generation = CONCAT('upgraded-', version) WHERE generation IS NULL";
        if (!initialize) {
            throw new IllegalStateException("Pending refresh table " + table + " has the structure of DDK 0.8 or earlier. Upgrade it with: "
                    + String.join("; ", upgrade) + "; " + backfill);
        }
        upgrade.forEach(jdbc::execute);
        jdbc.update(backfill);
    }

    private void prepareDueIndex(boolean initialize) {
        if (leadingIndexColumns().contains("available_at")) {
            return;
        }
        if (initialize) {
            jdbc.execute(dueIndex());
        } else {
            log.warn("Pending refresh table {} has no index on available_at, so every sweep scans the whole table. Add it with: {}",
                    table, dueIndex());
        }
    }

    private Set<String> existingColumns() {
        return inTable(metaData -> metaData::getColumns, "COLUMN_NAME", rs -> true);
    }

    /**
     * 各个索引的第一列。
     */
    private Set<String> leadingIndexColumns() {
        return inTable(metaData -> (catalog, schema, table, ignored) -> metaData.getIndexInfo(catalog, schema, table, false, true),
                "COLUMN_NAME", rs -> rs.getShort("ORDINAL_POSITION") == 1);
    }

    /**
     * 按表名原样、大写、小写依次查元数据：不同数据库对未加引号的标识符大小写处理不同。
     */
    private Set<String> inTable(Function<DatabaseMetaData, MetaDataQuery> query, String column, RowFilter filter) {
        Set<String> values = jdbc.execute((ConnectionCallback<Set<String>>) connection -> {
            DatabaseMetaData metaData = connection.getMetaData();
            String schema = table.contains(".") ? table.substring(0, table.indexOf('.')) : null;
            String name = table.contains(".") ? table.substring(table.indexOf('.') + 1) : table;
            for (String candidate : new String[] {name, name.toUpperCase(Locale.ROOT), name.toLowerCase(Locale.ROOT)}) {
                Set<String> found = new LinkedHashSet<>();
                try (ResultSet rs = query.apply(metaData).run(null, schema, candidate, null)) {
                    while (rs.next()) {
                        String value = rs.getString(column);
                        if (value != null && filter.test(rs)) {
                            found.add(value.toLowerCase(Locale.ROOT));
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

        ResultSet run(@Nullable String catalog, @Nullable String schema, String table, @Nullable String pattern) throws SQLException;
    }

    @FunctionalInterface
    private interface RowFilter {

        boolean test(ResultSet rs) throws SQLException;
    }

    private Timestamp now() {
        return Timestamp.from(clock.instant());
    }

    private static String newToken() {
        return UUID.randomUUID().toString();
    }

    /**
     * 一条到期的标记。
     */
    public record Pending(
            String projection,

            String id,

            String generation,

            int attempts
    ) {
    }

    /**
     * 一次领取：领取的是哪一代标记，以及这次领取的令牌。
     */
    public record Claim(
            Pending pending,

            String token
    ) {
    }
}
