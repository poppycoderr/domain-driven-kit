package com.ddk.projection.starter;

import com.ddk.compat.Releases;
import com.ddk.core.context.OperatorContext;
import com.ddk.projection.starter.internal.PendingRefreshes;
import com.ddk.projection.starter.internal.RefreshWorker;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 待刷新表的跨版本兼容：每个已发布版本的 {@code compat/<版本>/projection-pending-table.sql} 是那个版本的建表语句、到期索引和它写下的一个标记
 * （见仓库的 {@code compat/generator}）。当前版本不升级表结构就能用这张表，并按标记里的租户刷新。
 */
@DisplayName("与已发布版本的待刷新表兼容")
class PendingTableCompatibilityTest {

    @ParameterizedTest(name = "{0}")
    @MethodSource("com.ddk.compat.Releases#versions")
    @DisplayName("表结构和到期索引与已发布版本相同；它写下的标记，当前版本以它的租户刷新并删除")
    void processesAReleasedMark(String version) {
        List<String> statements = statements(version);
        DriverManagerDataSource dataSource = new DriverManagerDataSource("jdbc:h2:mem:pending-compat-" + version + ";DB_CLOSE_DELAY=-1", "sa", "");
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        statements.forEach(jdbc::execute);
        PendingRefreshes pending = new PendingRefreshes(jdbc, new DataSourceTransactionManager(dataSource), "ddk_projection_pending",
                Clock.fixed(Instant.parse("2026-10-23T08:00:00Z"), ZoneOffset.UTC));

        assertThat(pending.schema()).isEqualTo(statements.get(0));
        assertThat(pending.dueIndex()).isEqualTo(statements.get(1));
        pending.prepare(false);

        List<String> refreshed = new ArrayList<>();
        Projection catalog = new Projection() {

            @Override
            public String name() {
                return "catalog";
            }

            @Override
            public void refresh(String id) {
                refreshed.add(OperatorContext.required().tenantId() + "/" + id);
            }
        };
        RefreshWorker worker = new RefreshWorker(List.of(catalog), pending, 10, Duration.ofMinutes(1), Duration.ofSeconds(1),
                Duration.ofMinutes(1), Duration.ofSeconds(30));

        assertThat(worker.drain()).isEqualTo(1);
        assertThat(refreshed).containsExactly("tenant-a/42");
        assertThat(pending.count("catalog")).isZero();
    }

    private static List<String> statements(String version) {
        String sql = Releases.read(version, "projection-pending-table.sql");
        return Arrays.stream(sql.split(";\n")).map(String::strip).filter(statement -> !statement.isEmpty()).toList();
    }
}
