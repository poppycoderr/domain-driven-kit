package com.ddk.projection.starter;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ddk.core.context.Operator;
import com.ddk.core.context.OperatorContext;
import com.ddk.mybatis.starter.config.MybatisPlusAutoConfiguration;
import com.ddk.projection.starter.config.DdkProjectionAutoConfiguration;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceInitializationAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceTransactionManagerAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.JdbcTemplateAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * 投影和 {@code ddk-mybatis-starter} 的租户拦截器组合：后台线程刷新时恢复标记记录的租户，租户内唯一的标识互不影响；
 * 取不到租户时刷新失败并重试，而不是读到所有租户的数据。
 */
@SpringBootTest(classes = TenantProjectionIntegrationTest.TestApp.class, properties = {
        "spring.datasource.url=jdbc:h2:mem:tenant-projection;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.sql.init.mode=always",
        "spring.sql.init.schema-locations=classpath:tenant-projection-schema.sql",
        "ddk.mybatis.db-type=h2",
        "ddk.mybatis.tenant.enabled=true",
        "ddk.projection.sweep-interval=200ms",
        "ddk.projection.retry-delay=100ms"
})
@DisplayName("投影与多租户组合（H2）")
class TenantProjectionIntegrationTest {

    private static final Operator ALICE = Operator.of("alice", "1");

    private static final Operator BOB = Operator.of("bob", "2");

    @Autowired
    private OrderMapper orders;

    @Autowired
    private Projections projections;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private TransactionTemplate transaction;

    @BeforeEach
    void clean() {
        transaction = new TransactionTemplate(transactionManager);
        jdbc.update("DELETE FROM t_order");
        jdbc.update("DELETE FROM t_order_view");
        jdbc.update("DELETE FROM ddk_projection_pending");
    }

    @Test
    @DisplayName("两个租户各有一个 1 号订单：各自在自己的租户里标记，后台以各自的租户刷新，读模型互不覆盖")
    void eachTenantIsRefreshedInItsOwnContext() {
        place(ALICE, "PAID");
        place(BOB, "SHIPPED");

        await().atMost(Duration.ofSeconds(10)).until(() -> jdbc.queryForObject("SELECT COUNT(*) FROM t_order_view", Integer.class) == 2);
        assertThat(view()).containsExactlyInAnyOrder("1/1/PAID", "2/1/SHIPPED");
        assertThat(projections.pending("orders")).isZero();
    }

    @Test
    @DisplayName("在没有租户的地方标记：刷新被租户拦截器拒绝，错误留在待刷新表里等待重试，读模型不受影响")
    void aMarkWithoutTenantFailsClosed() {
        OperatorContext.runAs(ALICE, () -> orders.insert(order(1L, "PAID")));
        transaction.executeWithoutResult(status -> projections.markDirty("orders", 1L));

        await().atMost(Duration.ofSeconds(10)).until(() -> lastError() != null);
        assertThat(lastError()).contains("OperatorContext has no tenant");
        assertThat(view()).isEmpty();
        assertThat(projections.pending("orders")).isEqualTo(1);
    }

    private void place(Operator operator, String state) {
        OperatorContext.runAs(operator, () -> transaction.executeWithoutResult(status -> {
            orders.insert(order(1L, state));
            projections.markDirty("orders", 1L);
        }));
    }

    private List<String> view() {
        return jdbc.query("SELECT tenant_id, id, state FROM t_order_view", (rs, row) -> rs.getLong(1) + "/" + rs.getLong(2) + "/" + rs.getString(3));
    }

    private @Nullable String lastError() {
        List<String> errors = jdbc.query("SELECT last_error FROM ddk_projection_pending", (rs, row) -> rs.getString(1));
        return errors.isEmpty() ? null : errors.getFirst();
    }

    private static OrderPo order(Long id, String state) {
        OrderPo po = new OrderPo();
        po.id = id;
        po.state = state;
        return po;
    }

    @TableName("t_order")
    static class OrderPo {

        Long id;

        String state;
    }

    @Mapper
    interface OrderMapper extends BaseMapper<OrderPo> {

        @Select("SELECT state FROM t_order WHERE id = #{id}")
        @Nullable String stateOf(Long id);
    }

    /**
     * 读模型：从写模型读当前状态，按当前租户写进宽表。读写模型都经过租户拦截器，所以必须在正确的租户里执行。
     */
    static class OrderProjection implements Projection {

        private final OrderMapper orders;

        private final JdbcTemplate jdbc;

        OrderProjection(OrderMapper orders, JdbcTemplate jdbc) {
            this.orders = orders;
            this.jdbc = jdbc;
        }

        @Override
        public String name() {
            return "orders";
        }

        @Override
        public void refresh(String id) {
            String state = orders.stateOf(Long.valueOf(id));
            long tenant = Long.parseLong(OperatorContext.required().tenantId());
            jdbc.update("DELETE FROM t_order_view WHERE tenant_id = ? AND id = ?", tenant, Long.valueOf(id));
            if (state != null) {
                jdbc.update("INSERT INTO t_order_view (tenant_id, id, state) VALUES (?, ?, ?)", tenant, Long.valueOf(id), state);
            }
        }
    }

    @Configuration
    @MapperScan(basePackageClasses = TenantProjectionIntegrationTest.class, annotationClass = Mapper.class)
    @ImportAutoConfiguration({
            DataSourceAutoConfiguration.class,
            DataSourceInitializationAutoConfiguration.class,
            JdbcTemplateAutoConfiguration.class,
            DataSourceTransactionManagerAutoConfiguration.class,
            MybatisPlusAutoConfiguration.class,
            com.baomidou.mybatisplus.autoconfigure.MybatisPlusAutoConfiguration.class,
            DdkProjectionAutoConfiguration.class
    })
    static class TestApp {

        @Bean
        OrderProjection orderProjection(OrderMapper orders, JdbcTemplate jdbc) {
            return new OrderProjection(orders, jdbc);
        }
    }
}
