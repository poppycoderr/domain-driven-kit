package com.ddk.projection.starter.internal;

import com.ddk.projection.starter.FailedRefresh;
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
import java.util.ArrayList;
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
 * 标记按「读模型 + 租户 + 数据标识」区分：租户内唯一的标识在两个租户下是两条数据。租户随标记持久化，任何实例在任何时候接手都知道
 * 该以哪个租户刷新；没有租户时为空字符串。
 * <p>
 * {@code version} 列保留给 0.8 及更早的 worker：每次标记仍然加一，滚动升级期间旧 worker 的确认同样会落空。
 */
public class PendingRefreshes {

    private static final Logger log = LoggerFactory.getLogger(PendingRefreshes.class);

    private static final Pattern TABLE_NAME = Pattern.compile("[A-Za-z_][A-Za-z0-9_.]*");

    private static final int MAX_ERROR_LENGTH = 500;

    private static final int MAX_TENANT_LENGTH = 64;

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
    /**
     * @param tenant 数据所属的租户，没有租户时为空字符串
     * @throws IllegalArgumentException 租户标识超过 64 个字符
     */
    public void mark(String projection, String tenant, String id) {
        if (tenant.length() > MAX_TENANT_LENGTH) {
            throw new IllegalArgumentException("Tenant id longer than " + MAX_TENANT_LENGTH + " characters: " + tenant);
        }
        if (touch(projection, tenant, id) > 0) {
            return;
        }
        try {
            nested.executeWithoutResult(status -> jdbc.update("INSERT INTO " + table
                            + " (projection, tenant_id, entity_id, version, generation, attempts, available_at) VALUES (?, ?, ?, 1, ?, 0, ?)",
                    projection, tenant, id, newToken(), now()));
        } catch (DuplicateKeyException e) {
            touch(projection, tenant, id);
        }
    }

    private int touch(String projection, String tenant, String id) {
        return jdbc.update("UPDATE " + table + " SET version = version + 1, generation = ?, attempts = 0, available_at = ?, last_error = NULL "
                + "WHERE projection = ? AND tenant_id = ? AND entity_id = ?", newToken(), now(), projection, tenant, id);
    }

    /**
     * 已经到期、并且没有被别人领取（或领取的租约已过期）的标记，最早到期的在前。
     * <p>
     * 滚动升级期间还在运行的 0.8 实例插入的标记没有代次，这里给它补上一个，否则按代次领取永远领不到它。
     */
    public List<Pending> due(int limit) {
        Timestamp now = now();
        List<Row> due = jdbc.query("SELECT projection, tenant_id, entity_id, generation, attempts FROM " + table
                        + " WHERE available_at <= ? AND (claim_token IS NULL OR claimed_until < ?) ORDER BY available_at LIMIT " + limit,
                (rs, row) -> new Row(rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4), rs.getInt(5)), now, now);
        return due.stream().map(this::withGeneration).flatMap(Optional::stream).toList();
    }

    private Optional<Pending> withGeneration(Row row) {
        String generation = row.generation();
        if (generation == null) {
            generation = newToken();
            int adopted = jdbc.update("UPDATE " + table + " SET generation = ? "
                    + "WHERE projection = ? AND tenant_id = ? AND entity_id = ? AND generation IS NULL", generation, row.projection(), row.tenant(), row.id());
            if (adopted == 0) {
                return Optional.empty();
            }
        }
        return Optional.of(new Pending(row.projection(), row.tenant(), row.id(), generation, row.attempts()));
    }

    private record Row(
            String projection,

            String tenant,

            String id,

            @Nullable String generation,

            int attempts
    ) {
    }

    /**
     * 领取一条标记，租约为 {@code lease}。标记已经换了代次、或者被别人领取着时返回空。
     */
    public Optional<Claim> claim(Pending pending, Duration lease) {
        String token = newToken();
        Timestamp now = now();
        int claimed = jdbc.update("UPDATE " + table + " SET claim_token = ?, claimed_until = ? "
                        + "WHERE projection = ? AND tenant_id = ? AND entity_id = ? AND generation = ? AND (claim_token IS NULL OR claimed_until < ?)",
                token, Timestamp.from(clock.instant().plus(lease)), pending.projection(), pending.tenant(), pending.id(), pending.generation(), now);
        return claimed > 0 ? Optional.of(new Claim(pending, token)) : Optional.empty();
    }

    /**
     * 刷新完成。只有代次没变、自己仍是领取者时才删除标记；否则释放领取并重新标记，让这条数据再刷新一次。
     *
     * @return 标记是否已经删除
     */
    public boolean done(Claim claim) {
        Pending pending = claim.pending();
        int deleted = jdbc.update("DELETE FROM " + table
                        + " WHERE projection = ? AND tenant_id = ? AND entity_id = ? AND generation = ? AND claim_token = ?",
                pending.projection(), pending.tenant(), pending.id(), pending.generation(), claim.token());
        if (deleted > 0) {
            return true;
        }
        release(claim);
        mark(pending.projection(), pending.tenant(), pending.id());
        return false;
    }

    /**
     * 刷新失败，过 {@code delay} 之后再试，并释放领取。期间又被标记过的保持立即到期，不受这次失败影响。
     */
    public void failed(Claim claim, Duration delay, @Nullable String error) {
        Pending pending = claim.pending();
        String message = error == null ? null : error.substring(0, Math.min(error.length(), MAX_ERROR_LENGTH));
        int updated = jdbc.update("UPDATE " + table + " SET attempts = attempts + 1, available_at = ?, last_error = ?, "
                        + "claim_token = NULL, claimed_until = NULL "
                        + "WHERE projection = ? AND tenant_id = ? AND entity_id = ? AND generation = ? AND claim_token = ?",
                Timestamp.from(clock.instant().plus(delay)), message, pending.projection(), pending.tenant(), pending.id(), pending.generation(),
                claim.token());
        if (updated == 0) {
            release(claim);
        }
    }

    private void release(Claim claim) {
        Pending pending = claim.pending();
        jdbc.update("UPDATE " + table + " SET claim_token = NULL, claimed_until = NULL "
                        + "WHERE projection = ? AND tenant_id = ? AND entity_id = ? AND claim_token = ?",
                pending.projection(), pending.tenant(), pending.id(), claim.token());
    }

    public long count(String projection) {
        Long count = jdbc.queryForObject("SELECT COUNT(*) FROM " + table + " WHERE projection = ?", Long.class, projection);
        return count == null ? 0 : count;
    }

    /**
     * 至少失败过一次、还在等待重试的标记数量。
     */
    public long failing(String projection) {
        Long count = jdbc.queryForObject("SELECT COUNT(*) FROM " + table + " WHERE projection = ? AND attempts > 0", Long.class, projection);
        return count == null ? 0 : count;
    }

    /**
     * 已经到期的标记里等得最久的那条等了多久；没有到期的标记时为零。
     */
    public Duration oldestDueAge(String projection) {
        Timestamp now = now();
        Timestamp oldest = jdbc.queryForObject("SELECT MIN(available_at) FROM " + table + " WHERE projection = ? AND available_at <= ?",
                Timestamp.class, projection, now);
        return oldest == null ? Duration.ZERO : Duration.between(oldest.toInstant(), now.toInstant());
    }

    /**
     * 失败过的标记，失败次数多的在前。
     */
    public List<FailedRefresh> failures(String projection, int limit) {
        return jdbc.query("SELECT tenant_id, entity_id, attempts, last_error, available_at FROM " + table
                        + " WHERE projection = ? AND attempts > 0 ORDER BY attempts DESC, available_at LIMIT " + Math.max(limit, 0),
                (rs, row) -> new FailedRefresh(rs.getString(1), rs.getString(2), rs.getInt(3), rs.getString(4), rs.getTimestamp(5).toInstant()),
                projection);
    }

    /**
     * 让失败过的标记立即到期，不等退避结束。失败次数保留：再失败时退避从原来的位置继续。
     *
     * @return 受影响的标记数量
     */
    public int retryFailed(String projection) {
        return jdbc.update("UPDATE " + table + " SET available_at = ? WHERE projection = ? AND attempts > 0", now(), projection);
    }

    /**
     * 建表语句。{@code tenant_id} 有默认值，滚动升级期间还没升级的旧实例插入标记时不会失败。
     * <p>
     * {@code CREATE TABLE IF NOT EXISTS} 适用于 MySQL、PostgreSQL、H2；其他数据库请自行建表并关闭自动初始化。
     */
    public String schema() {
        return "CREATE TABLE IF NOT EXISTS " + table + " ("
                + "projection VARCHAR(100) NOT NULL, "
                + "tenant_id VARCHAR(" + MAX_TENANT_LENGTH + ") DEFAULT '' NOT NULL, "
                + "entity_id VARCHAR(200) NOT NULL, "
                + "version BIGINT NOT NULL, "
                + "generation VARCHAR(36), "
                + "claim_token VARCHAR(36), "
                + "claimed_until TIMESTAMP, "
                + "attempts INT NOT NULL, "
                + "available_at TIMESTAMP NOT NULL, "
                + "last_error VARCHAR(" + MAX_ERROR_LENGTH + "), "
                + "PRIMARY KEY (projection, tenant_id, entity_id))";
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
     * <p>
     * 表是旧结构（没有 {@code tenant_id}：0.8 或更早的 0.9 快照）时，主键要变，各数据库改主键的语法不同，所以复制重建：
     * 把旧表复制出来，按新结构重建，再把标记搬回去，已有的标记都算作没有租户。{@code initialize} 为 true 时直接执行；
     * 为 false 就启动失败，异常里给出这些语句，交给迁移工具。
     * <p>
     * 复制重建不是原子的（MySQL 的 DDL 会隐式提交），中途停下会留下副本表。启动时先检查副本，有就把上一次升级做完，
     * 而不是建一张空表、让副本里的标记无人处理。
     *
     * @throws IllegalStateException 表不存在、是旧结构或者有没做完的升级，并且不允许自动初始化
     */
    public void prepare(boolean initialize) {
        Set<String> columns = columnsOf(table);
        Set<String> copyColumns = columnsOf(copyTable());
        if (!copyColumns.isEmpty()) {
            resumeUpgrade(columns, copyColumns, initialize);
            return;
        }
        if (columns.isEmpty()) {
            if (!initialize) {
                throw new IllegalStateException("Pending refresh table " + table + " does not exist. Create it with: " + schema() + "; " + dueIndex());
            }
            jdbc.execute(schema());
            jdbc.execute(dueIndex());
            return;
        }
        if (!columns.contains("tenant_id")) {
            List<String> upgrade = upgrade(columns);
            if (!initialize) {
                throw new IllegalStateException("Pending refresh table " + table + " has the structure of DDK 0.8 or an earlier 0.9 snapshot. "
                        + "Upgrade it with: " + String.join("; ", upgrade));
            }
            log.info("Upgrading pending refresh table {} to the current structure", table);
            upgrade.forEach(jdbc::execute);
            return;
        }
        prepareDueIndex(initialize);
    }

    /**
     * 复制重建。不用 {@code RENAME}：PostgreSQL 重命名表之后旧主键的索引名不变，新表建主键时会撞名。
     * 领取信息不搬：升级时没有 worker 在处理。
     */
    private List<String> upgrade(Set<String> columns) {
        String copy = copyTable();
        return List.of(
                "CREATE TABLE " + copy + " AS SELECT * FROM " + table,
                "DROP TABLE " + table,
                schema(),
                dueIndex(),
                restore(columns),
                "DROP TABLE " + copy);
    }

    /**
     * 上一次复制重建在中途停下，留下了副本表：
     * <ul>
     *     <li>旧表还在（停在删除旧表之前）：旧表是完整的，副本作废，从头再升级一次；</li>
     *     <li>旧表已经删了，或者新表已经建好（停在搬回标记之前或者搬到一半）：建好新表，把新表里还没有的标记从副本搬回来，再删掉副本。</li>
     * </ul>
     * 两种情况重复执行都安全。
     */
    private void resumeUpgrade(Set<String> columns, Set<String> copyColumns, boolean initialize) {
        List<String> statements = new ArrayList<>();
        if (!columns.isEmpty() && !columns.contains("tenant_id")) {
            statements.add("DROP TABLE " + copyTable());
            statements.addAll(upgrade(columns));
        } else {
            statements.add(schema());
            if (columns.isEmpty() || !leadingIndexColumns().contains("available_at")) {
                statements.add(dueIndex());
            }
            statements.add(restore(copyColumns));
            statements.add("DROP TABLE " + copyTable());
        }
        if (!initialize) {
            throw new IllegalStateException("An interrupted upgrade of pending refresh table " + table + " left " + copyTable()
                    + " behind. Finish it with: " + String.join("; ", statements));
        }
        log.warn("Finishing the interrupted upgrade of pending refresh table {} from {}", table, copyTable());
        statements.forEach(jdbc::execute);
    }

    /**
     * 把副本里的标记搬进新表，跳过新表里已经有的，所以可以重复执行。副本是 0.8 结构时没有租户和代次，补上空租户和一个代次。
     */
    private String restore(Set<String> copyColumns) {
        String tenant = copyColumns.contains("tenant_id") ? "c.tenant_id" : "''";
        String generation = copyColumns.contains("generation") ? "COALESCE(c.generation, CONCAT('upgraded-', c.version))" : "CONCAT('upgraded-', c.version)";
        return "INSERT INTO " + table + " (projection, tenant_id, entity_id, version, generation, attempts, available_at, last_error) "
                + "SELECT c.projection, " + tenant + ", c.entity_id, c.version, " + generation + ", c.attempts, c.available_at, c.last_error "
                + "FROM " + copyTable() + " c WHERE NOT EXISTS (SELECT 1 FROM " + table + " p "
                + "WHERE p.projection = c.projection AND p.tenant_id = " + tenant + " AND p.entity_id = c.entity_id)";
    }

    private String copyTable() {
        return table + "_upgrade";
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

    private Set<String> columnsOf(String tableName) {
        return inTable(tableName, metaData -> metaData::getColumns, "COLUMN_NAME", rs -> true);
    }

    /**
     * 各个索引的第一列。
     */
    private Set<String> leadingIndexColumns() {
        return inTable(table, metaData -> (catalog, schema, name, ignored) -> metaData.getIndexInfo(catalog, schema, name, false, true),
                "COLUMN_NAME", rs -> rs.getShort("ORDINAL_POSITION") == 1);
    }

    /**
     * 按表名原样、大写、小写依次查元数据：不同数据库对未加引号的标识符大小写处理不同。
     */
    private Set<String> inTable(String tableName, Function<DatabaseMetaData, MetaDataQuery> query, String column, RowFilter filter) {
        Set<String> values = jdbc.execute((ConnectionCallback<Set<String>>) connection -> {
            DatabaseMetaData metaData = connection.getMetaData();
            String schema = tableName.contains(".") ? tableName.substring(0, tableName.indexOf('.')) : null;
            String name = tableName.contains(".") ? tableName.substring(tableName.indexOf('.') + 1) : tableName;
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

            String tenant,

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
