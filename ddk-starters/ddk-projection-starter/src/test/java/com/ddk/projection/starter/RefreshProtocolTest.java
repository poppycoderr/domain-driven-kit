package com.ddk.projection.starter;

import com.ddk.core.context.OperatorContext;
import com.ddk.projection.starter.internal.PendingRefreshes;
import com.ddk.projection.starter.internal.RefreshWorker;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 刷新协议在交错执行下的正确性：两个 worker 共用一张待刷新表，用 latch 精确控制谁先谁后，不靠 sleep 碰运气。
 * <p>
 * 源数据是一张 {@code t_source} 表，读模型是内存里的 Map。断言的是最终状态：标记清空时，读模型必须等于源数据。
 * 在 H2、MySQL、PostgreSQL 上运行同一组用例，见子类。
 */
@DisplayName("投影刷新协议（H2）")
class RefreshProtocolTest {

    private static final Duration LEASE = Duration.ofSeconds(30);

    protected JdbcTemplate jdbc;

    private TransactionTemplate transaction;

    private MutableClock clock;

    private PendingRefreshes pending;

    private final Map<String, String> view = new ConcurrentHashMap<>();

    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    protected DataSource dataSource() {
        return new DriverManagerDataSource("jdbc:h2:mem:refresh-protocol;DB_CLOSE_DELAY=-1", "sa", "");
    }

    @BeforeEach
    void setUp() {
        DataSource dataSource = dataSource();
        jdbc = new JdbcTemplate(dataSource);
        DataSourceTransactionManager transactionManager = new DataSourceTransactionManager(dataSource);
        transaction = new TransactionTemplate(transactionManager);
        clock = new MutableClock(Instant.parse("2030-01-01T00:00:00Z"));
        jdbc.execute("DROP TABLE IF EXISTS protocol_pending");
        jdbc.execute("DROP TABLE IF EXISTS t_source");
        jdbc.execute("CREATE TABLE t_source (id VARCHAR(50) PRIMARY KEY, state VARCHAR(50) NOT NULL)");
        pending = new PendingRefreshes(jdbc, transactionManager, "protocol_pending", clock);
        pending.prepare(true);
    }

    @AfterEach
    void tearDown() {
        executor.shutdownNow();
    }

    @Test
    @DisplayName("A 读到 v1 后停顿，v2 提交并被 B 写入；A 恢复写回 v1，确认落空并重新标记，最终收敛到 v2")
    void aStaleRefreshThatFinishesLastIsCorrected() throws Exception {
        change("1", "v1");
        Paused paused = new Paused();
        RefreshWorker slow = worker(paused);
        RefreshWorker fast = worker(this::copy);

        Future<Integer> old = executor.submit(slow::drain);
        paused.awaitCaptured();
        change("1", "v2");
        clock.advance(LEASE.plusSeconds(1));
        assertThat(fast.drain()).as("B takes over after the lease expired and writes v2").isEqualTo(1);
        assertThat(view).containsEntry("1", "v2");
        paused.resume();

        assertThat(old.get(10, TimeUnit.SECONDS)).as("A's acknowledgement misses").isZero();
        assertThat(view).as("A wrote its stale snapshot").containsEntry("1", "v1");
        assertThat(pending.count("catalog")).as("A marked the entry again").isEqualTo(1);
        drainUntilEmpty(fast);
        assertThat(view).containsEntry("1", "v2");
    }

    @Test
    @DisplayName("租约未过期时别人领取不到；A 处理期间的新变化让它的确认落空，随后再刷新一次")
    void aChangeDuringAClaimedRefreshTriggersAnotherOne() throws Exception {
        change("1", "v1");
        Paused paused = new Paused();
        RefreshWorker slow = worker(paused);
        RefreshWorker fast = worker(this::copy);

        Future<Integer> old = executor.submit(slow::drain);
        paused.awaitCaptured();
        change("1", "v2");
        assertThat(fast.drain()).as("the claim is still held").isZero();
        paused.resume();
        old.get(10, TimeUnit.SECONDS);

        assertThat(pending.count("catalog")).isEqualTo(1);
        drainUntilEmpty(fast);
        assertThat(view).containsEntry("1", "v2");
    }

    @Test
    @DisplayName("删除与旧刷新交错：旧 worker 把删除前的数据写回，确认落空，再刷新一次又删掉，不会复活")
    void aStaleRefreshCannotResurrectADeletion() throws Exception {
        change("1", "v1");
        Paused paused = new Paused();
        RefreshWorker slow = worker(paused);
        RefreshWorker fast = worker(this::copy);

        Future<Integer> old = executor.submit(slow::drain);
        paused.awaitCaptured();
        transaction.executeWithoutResult(status -> {
            jdbc.update("DELETE FROM t_source WHERE id = '1'");
            pending.mark("catalog", "", "1");
        });
        clock.advance(LEASE.plusSeconds(1));
        fast.drain();
        assertThat(view).doesNotContainKey("1");
        paused.resume();
        old.get(10, TimeUnit.SECONDS);
        assertThat(view).as("resurrected for a moment").containsKey("1");

        drainUntilEmpty(fast);
        assertThat(view).doesNotContainKey("1");
    }

    @Test
    @DisplayName("代次不复用：删除后重新插入的标记，旧句柄的确认与失败都碰不到它")
    void anOldHandleCannotTouchANewGeneration() {
        pending.mark("catalog", "", "2");
        PendingRefreshes.Claim old = pending.claim(pending.due(10).getFirst(), LEASE).orElseThrow();
        jdbc.update("DELETE FROM protocol_pending WHERE entity_id = '2'");
        pending.mark("catalog", "", "2");
        String fresh = jdbc.queryForObject("SELECT generation FROM protocol_pending WHERE entity_id = '2'", String.class);

        pending.failed(old, Duration.ofHours(1), "late failure");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM protocol_pending WHERE entity_id = '2' AND attempts = 0 AND last_error IS NULL",
                Long.class)).as("the new generation is untouched by the old failure").isEqualTo(1);

        assertThat(pending.done(old)).isFalse();
        assertThat(pending.count("catalog")).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT generation FROM protocol_pending WHERE entity_id = '2'", String.class))
                .as("the missed acknowledgement marked it again with a newer generation").isNotEqualTo(fresh);
    }

    @Test
    @DisplayName("重建与实时更新交错，两个 worker 一起处理；停止写入后标记清空，读模型与源数据一致")
    void rebuildAndLiveUpdatesConverge() throws Exception {
        for (int i = 1; i <= 20; i++) {
            change(String.valueOf(i), "v1");
        }
        view.clear();
        RefreshWorker first = worker(this::copy);
        RefreshWorker second = worker(this::copy);
        jdbc.query("SELECT id FROM t_source", rs -> {
            pending.mark("catalog", "", rs.getString(1));
        });

        CountDownLatch go = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(3);
        try {
            Future<?> writer = pool.submit(() -> {
                await(go);
                for (int round = 2; round <= 5; round++) {
                    for (int i = 1; i <= 20; i += 3) {
                        change(String.valueOf(i), "v" + round);
                    }
                }
                return null;
            });
            Future<?> a = pool.submit(() -> {
                await(go);
                for (int i = 0; i < 20; i++) {
                    first.drain();
                }
                return null;
            });
            Future<?> b = pool.submit(() -> {
                await(go);
                for (int i = 0; i < 20; i++) {
                    second.drain();
                }
                return null;
            });
            go.countDown();
            writer.get(30, TimeUnit.SECONDS);
            a.get(30, TimeUnit.SECONDS);
            b.get(30, TimeUnit.SECONDS);
        } finally {
            pool.shutdownNow();
        }
        drainUntilEmpty(first);

        Map<String, String> source = new ConcurrentHashMap<>();
        jdbc.query("SELECT id, state FROM t_source", rs -> {
            source.put(rs.getString(1), rs.getString(2));
        });
        assertThat(view).isEqualTo(source);
    }

    @Test
    @DisplayName("worker 在处理中途失联：租约过期后别人接手完成；回滚的事务不留下标记")
    void anAbandonedClaimIsTakenOverAndRollbacksLeaveNoMark() {
        change("1", "v1");
        Optional<PendingRefreshes.Claim> abandoned = pending.claim(pending.due(10).getFirst(), LEASE);
        assertThat(abandoned).isPresent();
        RefreshWorker survivor = worker(this::copy);
        assertThat(survivor.drain()).as("still leased").isZero();

        clock.advance(LEASE.plusSeconds(1));
        assertThat(survivor.drain()).isEqualTo(1);
        assertThat(view).containsEntry("1", "v1");
        assertThat(pending.count("catalog")).isZero();

        assertThatThrownBy(() -> transaction.executeWithoutResult(status -> {
            jdbc.update("UPDATE t_source SET state = 'v9' WHERE id = '1'");
            pending.mark("catalog", "", "1");
            throw new IllegalStateException("rejected");
        })).hasMessage("rejected");
        assertThat(pending.count("catalog")).isZero();
    }

    @Test
    @DisplayName("刷新失败时释放领取并按退避推迟；推迟期间的新变化让它立即到期")
    void aFailureReleasesTheClaimAndBacksOff() {
        change("1", "v1");
        RefreshWorker failing = worker(id -> {
            throw new IllegalStateException("index unavailable");
        });
        assertThat(failing.drain()).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM protocol_pending WHERE claim_token IS NULL AND attempts = 1", Long.class))
                .isEqualTo(1);
        assertThat(pending.due(10)).as("backing off").isEmpty();

        change("1", "v2");
        assertThat(pending.due(10)).as("a new change is due at once").hasSize(1);
    }

    @Test
    @DisplayName("从 0.8 的表结构升级：复制重建，已有标记补上代次、算作没有租户，照常处理；升级后旧实例插入的标记也能处理；不允许自动升级时给出语句并启动失败")
    void upgradesATableFromTheOldStructure() {
        jdbc.execute("DROP TABLE protocol_pending");
        jdbc.execute("CREATE TABLE protocol_pending (projection VARCHAR(100) NOT NULL, entity_id VARCHAR(200) NOT NULL, version BIGINT NOT NULL, "
                + "attempts INT NOT NULL, available_at TIMESTAMP NOT NULL, last_error VARCHAR(500), PRIMARY KEY (projection, entity_id))");
        jdbc.update("INSERT INTO protocol_pending (projection, entity_id, version, attempts, available_at) VALUES ('catalog', '1', 3, 0, ?)",
                java.sql.Timestamp.from(clock.instant()));
        jdbc.update("INSERT INTO t_source (id, state) VALUES ('1', 'v1')");

        assertThatThrownBy(() -> pending.prepare(false))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("DDK 0.8 or an earlier 0.9 snapshot")
                .hasMessageContaining("CREATE TABLE protocol_pending_upgrade AS SELECT");

        pending.prepare(true);
        pending.prepare(true);
        assertThat(jdbc.queryForObject("SELECT generation FROM protocol_pending", String.class)).isEqualTo("upgraded-3");
        assertThat(jdbc.queryForObject("SELECT tenant_id FROM protocol_pending", String.class)).isEmpty();
        assertThatThrownBy(() -> jdbc.execute(pending.dueIndex())).as("the upgrade also adds the due index").isInstanceOf(DataAccessException.class);
        jdbc.execute("CREATE TABLE protocol_pending_upgrade (x INT)");
        jdbc.execute("DROP TABLE protocol_pending_upgrade");
        pending.mark("catalog", "tenant-a", "1");
        assertThat(pending.count("catalog")).as("the new primary key includes the tenant").isEqualTo(2);
        jdbc.update("DELETE FROM protocol_pending WHERE tenant_id = 'tenant-a'");
        jdbc.update("INSERT INTO protocol_pending (projection, entity_id, version, attempts, available_at) VALUES ('catalog', '2', 1, 0, ?)",
                java.sql.Timestamp.from(clock.instant()));
        jdbc.update("INSERT INTO t_source (id, state) VALUES ('2', 'v1')");

        drainUntilEmpty(worker(this::copy));
        assertThat(view).as("a mark inserted by a 0.8 instance during a rolling upgrade is adopted").containsEntry("1", "v1").containsEntry("2", "v1");
    }

    @Test
    @DisplayName("从 0.9 快照的表结构升级：保留已有的代次")
    void upgradesASnapshotTableKeepingGenerations() {
        jdbc.execute("DROP TABLE protocol_pending");
        jdbc.execute("CREATE TABLE protocol_pending (projection VARCHAR(100) NOT NULL, entity_id VARCHAR(200) NOT NULL, version BIGINT NOT NULL, "
                + "generation VARCHAR(36), claim_token VARCHAR(36), claimed_until TIMESTAMP, attempts INT NOT NULL, available_at TIMESTAMP NOT NULL, "
                + "last_error VARCHAR(500), PRIMARY KEY (projection, entity_id))");
        jdbc.update("INSERT INTO protocol_pending (projection, entity_id, version, generation, attempts, available_at) "
                + "VALUES ('catalog', '1', 3, 'kept', 2, ?)", java.sql.Timestamp.from(clock.instant()));

        pending.prepare(true);

        assertThat(jdbc.queryForObject("SELECT generation FROM protocol_pending", String.class)).isEqualTo("kept");
        assertThat(jdbc.queryForObject("SELECT attempts FROM protocol_pending", Integer.class)).isEqualTo(2);
    }

    @Test
    @DisplayName("同一个标识在两个租户下是两条标记，各自以系统操作者加自己的租户刷新；没有租户的不设置操作者；刷新后线程上不留操作者")
    void eachMarkIsRefreshedInItsOwnTenant() {
        pending.mark("catalog", "tenant-a", "1");
        pending.mark("catalog", "tenant-b", "1");
        pending.mark("catalog", "", "2");
        assertThat(pending.count("catalog")).isEqualTo(3);

        List<String> seen = new java.util.concurrent.CopyOnWriteArrayList<>();
        drainUntilEmpty(worker(id -> seen.add(id + "@" + OperatorContext.current()
                .map(operator -> operator.id() + "/" + operator.tenantId()).orElse("none"))));

        assertThat(seen).containsExactlyInAnyOrder("1@ddk-projection/tenant-a", "1@ddk-projection/tenant-b", "2@none");
        assertThat(OperatorContext.current()).isEmpty();
    }

    @Test
    @DisplayName("租户随标记持久化：换一个实例（重启或别的实例接手）照样以原来的租户刷新；失败重试时也是")
    void theTenantSurvivesARestartAndRetries() {
        pending.mark("catalog", "tenant-a", "1");
        PendingRefreshes afterRestart = new PendingRefreshes(jdbc, new DataSourceTransactionManager(java.util.Objects.requireNonNull(jdbc.getDataSource())), "protocol_pending", clock);
        java.util.concurrent.atomic.AtomicInteger calls = new java.util.concurrent.atomic.AtomicInteger();
        List<String> tenants = new java.util.concurrent.CopyOnWriteArrayList<>();
        Projection failingOnce = new Projection() {

            @Override
            public String name() {
                return "catalog";
            }

            @Override
            public void refresh(String id) {
                tenants.add(OperatorContext.required().tenantId());
                if (calls.incrementAndGet() == 1) {
                    throw new IllegalStateException("source unavailable");
                }
            }
        };
        RefreshWorker worker = new RefreshWorker(List.of(failingOnce), afterRestart, 10, Duration.ofMinutes(1), Duration.ofMinutes(1),
                Duration.ofHours(1), LEASE);

        worker.drain();
        assertThat(jdbc.queryForObject("SELECT last_error FROM protocol_pending", String.class)).contains("source unavailable");
        clock.advance(Duration.ofMinutes(2));
        worker.drain();

        assertThat(tenants).containsExactly("tenant-a", "tenant-a");
        assertThat(afterRestart.count("catalog")).isZero();
        assertThat(OperatorContext.current()).isEmpty();
    }

    @Test
    @DisplayName("租户标识超过 64 个字符时拒绝标记")
    void rejectsOverlongTenants() {
        assertThatThrownBy(() -> pending.mark("catalog", "t".repeat(65), "1")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("新建的表带到期索引；已有的表缺索引时，允许初始化就补上，不允许只记录警告、照常启动")
    void theDueScanHasAnIndex() {
        assertThatThrownBy(() -> jdbc.execute(pending.dueIndex())).as("created with the table").isInstanceOf(DataAccessException.class);

        jdbc.execute("DROP TABLE protocol_pending");
        jdbc.execute(pending.schema());
        pending.prepare(false);
        jdbc.execute(pending.dueIndex());

        jdbc.execute("DROP TABLE protocol_pending");
        jdbc.execute(pending.schema());
        pending.prepare(true);
        pending.prepare(true);
        assertThatThrownBy(() -> jdbc.execute(pending.dueIndex())).as("added at startup").isInstanceOf(DataAccessException.class);
    }

    private void change(String id, String state) {
        transaction.executeWithoutResult(status -> {
            if (jdbc.update("UPDATE t_source SET state = ? WHERE id = ?", state, id) == 0) {
                jdbc.update("INSERT INTO t_source (id, state) VALUES (?, ?)", id, state);
            }
            pending.mark("catalog", "", id);
        });
    }

    private void copy(String id) {
        Optional<String> state = jdbc.query("SELECT state FROM t_source WHERE id = ?", (rs, row) -> rs.getString(1), id).stream().findFirst();
        state.ifPresentOrElse(value -> view.put(id, value), () -> view.remove(id));
    }

    private void drainUntilEmpty(RefreshWorker worker) {
        for (int i = 0; i < 50 && pending.count("catalog") > 0; i++) {
            worker.drain();
        }
        assertThat(pending.count("catalog")).as("all marks processed").isZero();
    }

    private RefreshWorker worker(java.util.function.Consumer<String> refresh) {
        Projection projection = new Projection() {

            @Override
            public String name() {
                return "catalog";
            }

            @Override
            public void refresh(String id) {
                refresh.accept(id);
            }
        };
        return new RefreshWorker(List.of(projection), pending, 10, Duration.ofMinutes(1), Duration.ofMinutes(1), Duration.ofHours(1), LEASE);
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * 读到源数据之后停住，等测试放行再写读模型。
     */
    private final class Paused implements java.util.function.Consumer<String> {

        private final CountDownLatch captured = new CountDownLatch(1);

        private final CountDownLatch resume = new CountDownLatch(1);

        @Override
        public void accept(String id) {
            AtomicReference<Optional<String>> snapshot = new AtomicReference<>(
                    jdbc.query("SELECT state FROM t_source WHERE id = ?", (rs, row) -> rs.getString(1), id).stream().findFirst());
            captured.countDown();
            await(resume);
            snapshot.get().ifPresentOrElse(value -> view.put(id, value), () -> view.remove(id));
        }

        void awaitCaptured() throws InterruptedException {
            assertThat(captured.await(10, TimeUnit.SECONDS)).as("the slow worker read the source").isTrue();
        }

        void resume() {
            resume.countDown();
        }
    }

    /**
     * 可以拨快的时钟，用来让租约过期，而不必真的等。
     */
    private static final class MutableClock extends Clock {

        private volatile Instant now;

        MutableClock(Instant now) {
            this.now = now;
        }

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public java.time.ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
