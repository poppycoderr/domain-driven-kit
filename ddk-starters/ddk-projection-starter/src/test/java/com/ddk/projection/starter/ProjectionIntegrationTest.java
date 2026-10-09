package com.ddk.projection.starter;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

/**
 * 写模型是一张 {@code t_product} 表，读模型是内存里的一个 Map，中间由待刷新表和后台线程连接。
 * 在 H2、MySQL、PostgreSQL 上跑同一组用例，见两个子类。
 */
@SpringBootTest(classes = ProjectionIntegrationTest.TestApplication.class, properties = {
        "spring.datasource.url=jdbc:h2:mem:projection;DB_CLOSE_DELAY=-1",
        "ddk.projection.sweep-interval=200ms",
        "ddk.projection.retry-delay=100ms",
        "ddk.projection.batch-size=3"
})
@DisplayName("读模型投影（H2）")
class ProjectionIntegrationTest {

    @Autowired
    Projections projections;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    TransactionTemplate transaction;

    @Autowired
    CatalogProjection catalog;

    @BeforeEach
    void reset() {
        await().atMost(Duration.ofSeconds(10)).until(() -> projections.pending("catalog") == 0);
        jdbc.execute("CREATE TABLE IF NOT EXISTS t_product (id VARCHAR(50) PRIMARY KEY, name VARCHAR(100) NOT NULL)");
        jdbc.update("DELETE FROM t_product");
        catalog.reset();
    }

    @Test
    @DisplayName("事务里标记，提交之后读模型跟上；同一条数据标记多次只刷新一次")
    void theReadModelFollowsACommittedChange() {
        transaction.executeWithoutResult(status -> {
            jdbc.update("INSERT INTO t_product (id, name) VALUES ('p1', 'keyboard')");
            projections.markDirty("catalog", "p1");
            jdbc.update("UPDATE t_product SET name = 'mechanical keyboard' WHERE id = 'p1'");
            projections.markDirty("catalog", "p1");
            projections.markDirty("catalog", "p1");
            assertThat(catalog.view).as("nothing is refreshed before the commit").isEmpty();
        });

        await().atMost(Duration.ofSeconds(5)).until(() -> projections.pending("catalog") == 0);
        assertThat(catalog.view).containsEntry("p1", "mechanical keyboard");
        assertThat(catalog.refreshes).containsExactly("p1");
    }

    @Test
    @DisplayName("回滚的事务不留下标记，读模型不变")
    void aRollbackLeavesNoMark() {
        assertThatThrownBy(() -> transaction.executeWithoutResult(status -> {
            jdbc.update("INSERT INTO t_product (id, name) VALUES ('p2', 'mouse')");
            projections.markDirty("catalog", "p2");
            throw new IllegalStateException("rejected");
        })).hasMessage("rejected");

        assertThat(projections.pending("catalog")).isZero();
        assertThat(catalog.refreshes).isEmpty();
    }

    @Test
    @DisplayName("写模型里删掉的数据，刷新时从读模型里移除；没有事务时标记后立即刷新")
    void deletionsReachTheReadModel() {
        jdbc.update("INSERT INTO t_product (id, name) VALUES ('p3', 'monitor')");
        projections.markDirty("catalog", "p3");
        await().atMost(Duration.ofSeconds(5)).until(() -> catalog.view.containsKey("p3"));

        jdbc.update("DELETE FROM t_product WHERE id = 'p3'");
        projections.markDirty("catalog", "p3");

        await().atMost(Duration.ofSeconds(5)).until(() -> !catalog.view.containsKey("p3"));
    }

    @Test
    @DisplayName("刷新失败的稍后重试，直到成功；失败期间标记留在表里并记下原因")
    void failedRefreshesAreRetried() {
        jdbc.update("INSERT INTO t_product (id, name) VALUES ('p4', 'webcam')");
        catalog.failuresLeft.set(2);

        projections.markDirty("catalog", "p4");

        await().atMost(Duration.ofSeconds(5)).until(() -> catalog.failuresLeft.get() < 2);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ddk_projection_pending WHERE last_error LIKE '%index unavailable%'", Long.class))
                .isPositive();
        await().atMost(Duration.ofSeconds(10)).until(() -> projections.pending("catalog") == 0);
        assertThat(catalog.view).containsEntry("p4", "webcam");
        assertThat(catalog.refreshes).containsExactly("p4", "p4", "p4");
    }

    @Test
    @DisplayName("刷新期间这条数据又变了：标记不会被这次刷新删掉，随后再刷新一次，读模型停在最新状态")
    void aChangeDuringARefreshIsNotLost() {
        jdbc.update("INSERT INTO t_product (id, name) VALUES ('p5', 'v1')");
        catalog.duringRefresh = id -> {
            catalog.duringRefresh = ignored -> {
            };
            jdbc.update("UPDATE t_product SET name = 'v2' WHERE id = ?", id);
            projections.markDirty("catalog", id);
        };

        projections.markDirty("catalog", "p5");

        await().atMost(Duration.ofSeconds(5)).until(() -> "v2".equals(catalog.view.get("p5")));
        await().atMost(Duration.ofSeconds(5)).until(() -> projections.pending("catalog") == 0);
        assertThat(catalog.refreshes).containsExactly("p5", "p5");
    }

    @Test
    @DisplayName("重建：写模型里的每一条都被刷新一遍，超过一批的数量也能处理完")
    void rebuildRefreshesEverything() {
        for (int i = 1; i <= 8; i++) {
            jdbc.update("INSERT INTO t_product (id, name) VALUES (?, ?)", "r" + i, "product " + i);
        }

        assertThat(projections.rebuild("catalog")).isEqualTo(8);

        await().atMost(Duration.ofSeconds(10)).until(() -> projections.pending("catalog") == 0);
        assertThat(catalog.view).hasSize(8).containsEntry("r8", "product 8");
    }

    @Test
    @DisplayName("没有这个名称的读模型、不支持重建的读模型，都在调用处报错")
    void unknownProjectionsAndUnsupportedRebuildsFailWhereCalled() {
        assertThatThrownBy(() -> projections.markDirty("orders", "1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("No projection named orders")
                .hasMessageContaining("catalog");
        assertThatThrownBy(() -> projections.rebuild("audit"))
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessageContaining("implement forEachId");
    }

    static class CatalogProjection implements Projection {

        final Map<String, String> view = new ConcurrentHashMap<>();

        final List<String> refreshes = new CopyOnWriteArrayList<>();

        final AtomicInteger failuresLeft = new AtomicInteger();

        volatile Consumer<String> duringRefresh = id -> {
        };

        private final JdbcTemplate jdbc;

        CatalogProjection(JdbcTemplate jdbc) {
            this.jdbc = jdbc;
        }

        void reset() {
            view.clear();
            refreshes.clear();
            failuresLeft.set(0);
            duringRefresh = id -> {
            };
        }

        @Override
        public String name() {
            return "catalog";
        }

        @Override
        public void refresh(String id) {
            refreshes.add(id);
            if (failuresLeft.getAndDecrement() > 0) {
                throw new IllegalStateException("index unavailable");
            }
            Optional<String> name = jdbc.query("SELECT name FROM t_product WHERE id = ?", (rs, row) -> rs.getString(1), id).stream().findFirst();
            duringRefresh.accept(id);
            name.ifPresentOrElse(value -> view.put(id, value), () -> view.remove(id));
        }

        @Override
        public void forEachId(Consumer<String> ids) {
            jdbc.query("SELECT id FROM t_product ORDER BY id", rs -> {
                ids.accept(rs.getString(1));
            });
        }
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    static class TestApplication {

        @Bean
        CatalogProjection catalogProjection(JdbcTemplate jdbc) {
            return new CatalogProjection(jdbc);
        }

        @Bean
        Projection auditProjection() {
            return new Projection() {

                @Override
                public String name() {
                    return "audit";
                }

                @Override
                public void refresh(String id) {
                }
            };
        }
    }
}
