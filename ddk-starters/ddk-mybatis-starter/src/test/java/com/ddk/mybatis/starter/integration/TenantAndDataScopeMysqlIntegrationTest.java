package com.ddk.mybatis.starter.integration;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ddk.core.context.DataScope;
import com.ddk.core.context.Operator;
import com.ddk.core.context.OperatorContext;
import com.ddk.mybatis.starter.config.MybatisPlusAutoConfiguration;
import com.ddk.mybatis.starter.datascope.DataScopeResolver;
import com.ddk.test.containers.DdkContainers;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceInitializationAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.JdbcTemplateAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 多租户和数据权限同时开启，在真实的 MySQL 上执行：两层条件都加上了，先按租户圈定，再在租户之内按数据范围收窄。
 * 两个租户故意用了相同的部门编号，只加其中一层条件就会读到对方的数据。
 */
@Testcontainers(disabledWithoutDocker = true)
@DirtiesContext
@SpringBootTest(classes = TenantAndDataScopeMysqlIntegrationTest.TestApp.class, properties = {
        "spring.sql.init.mode=always",
        "spring.sql.init.schema-locations=classpath:tenant-data-scope-schema.sql",
        "ddk.mybatis.db-type=mysql",
        "ddk.mybatis.tenant.enabled=true",
        "ddk.mybatis.data-scope.enabled=true",
        "ddk.mybatis.data-scope.tables=t_deal"
})
@DisplayName("多租户 + 行级数据权限（MySQL）")
class TenantAndDataScopeMysqlIntegrationTest {

    @Container
    static final MySQLContainer MYSQL = DdkContainers.mysql();

    /** 租户 1，销售部（10）的主管 */
    private static final Operator MANAGER = Operator.of("1", "1");

    /** 租户 1 的普通员工，只看自己创建的 */
    private static final Operator CLERK = Operator.of("2", "1");

    /** 租户 1 的管理员 */
    private static final Operator ADMIN = Operator.of("9", "1");

    /** 租户 2，同样是部门 10 的主管 */
    private static final Operator OTHER_TENANT_MANAGER = Operator.of("1", "2");

    @Autowired
    private DealMapper deals;

    @Autowired
    private JdbcTemplate jdbc;

    @DynamicPropertySource
    static void mysql(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
    }

    @BeforeEach
    void seed() {
        jdbc.update("DELETE FROM t_deal");
        jdbc.update("DELETE FROM t_dept");
        jdbc.update("INSERT INTO t_dept (id, name, tenant_id) VALUES (10, 'sales', 1), (20, 'support', 1), (1010, 'sales', 2)");
        jdbc.update("INSERT INTO t_deal (id, title, tenant_id, dept_id, create_by) VALUES "
                + "(1, 't1 sales by manager', 1, 10, 1), (2, 't1 sales by clerk', 1, 10, 2), (3, 't1 support by clerk', 1, 20, 2), "
                + "(4, 't1 support by someone', 1, 20, 5), (5, 't2 sales by manager', 2, 10, 1), (6, 't2 sales by clerk', 2, 10, 2)");
    }

    @Test
    @DisplayName("两层条件同时生效：同一个部门编号、同一个用户编号，在另一个租户里的数据读不到")
    void bothConditionsApply() {
        assertThat(ids(MANAGER)).containsExactly(1L, 2L);
        assertThat(ids(CLERK)).containsExactly(2L, 3L);
        assertThat(ids(ADMIN)).as("unrestricted inside the tenant, still not across tenants").containsExactly(1L, 2L, 3L, 4L);
        assertThat(ids(OTHER_TENANT_MANAGER)).containsExactly(5L, 6L);
    }

    @Test
    @DisplayName("分页的计数、联表查询、按主键读取都带着两层条件")
    void pagingJoinsAndLookups() {
        OperatorContext.runAs(MANAGER, () -> {
            Page<DealPo> page = deals.selectPage(new Page<>(1, 1), null);
            assertThat(page.getTotal()).isEqualTo(2);
            assertThat(page.getRecords()).hasSize(1);
            assertThat(deals.titlesWithDepartment()).containsExactly("t1 sales by manager @ sales", "t1 sales by clerk @ sales");
            assertThat(deals.selectById(3L)).as("same tenant, outside the scope").isNull();
            assertThat(deals.selectById(5L)).as("inside the scope by department number, other tenant").isNull();
        });
    }

    @Test
    @DisplayName("范围之外和租户之外的数据都改不了、删不掉；范围之内的更新照常带乐观锁")
    void writesAreConfinedToo() {
        OperatorContext.runAs(MANAGER, () -> {
            assertThat(deals.deleteById(4L)).isZero();
            assertThat(deals.deleteById(5L)).isZero();

            DealPo own = deals.selectById(1L);
            DealPo stale = deals.selectById(1L);
            own.title = "renamed";
            stale.title = "lost update";
            assertThat(deals.updateById(own)).isEqualTo(1);
            assertThat(deals.updateById(stale)).as("stale version").isZero();
        });

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM t_deal", Integer.class)).isEqualTo(6);
        assertThat(jdbc.queryForObject("SELECT CONCAT(title, '/', version) FROM t_deal WHERE id = 1", String.class)).isEqualTo("renamed/1");
    }

    @Test
    @DisplayName("没有租户或没有操作者时语句失败")
    void failsClosed() {
        assertThatThrownBy(() -> deals.selectList(null)).isInstanceOf(RuntimeException.class);
        assertThatThrownBy(() -> OperatorContext.runAs(Operator.of("1"), () -> deals.selectList(null)))
                .hasMessageContaining("OperatorContext has no tenant");
    }

    private List<Long> ids(Operator operator) {
        return OperatorContext.callAs(operator, () -> deals.selectList(null).stream().map(po -> po.id).sorted().toList());
    }

    @TableName("t_deal")
    static class DealPo {

        @TableId
        Long id;

        String title;

        Long deptId;

        Long createBy;

        @Version
        Long version;
    }

    @Mapper
    interface DealMapper extends BaseMapper<DealPo> {

        @Select("SELECT CONCAT(d.title, ' @ ', p.name) FROM t_deal d JOIN t_dept p ON p.id = d.dept_id ORDER BY d.id")
        List<String> titlesWithDepartment();
    }

    @Configuration
    @MapperScan(basePackageClasses = TenantAndDataScopeMysqlIntegrationTest.class, annotationClass = Mapper.class)
    @ImportAutoConfiguration({
            DataSourceAutoConfiguration.class,
            DataSourceInitializationAutoConfiguration.class,
            JdbcTemplateAutoConfiguration.class,
            MybatisPlusAutoConfiguration.class,
            com.baomidou.mybatisplus.autoconfigure.MybatisPlusAutoConfiguration.class,
    })
    static class TestApp {

        @Bean
        DataScopeResolver dataScopeResolver() {
            return operator -> switch (operator.id()) {
                case "1" -> DataScope.ofGroups(List.of("10"));
                case "2" -> DataScope.ownOnly();
                case "9" -> DataScope.all();
                default -> DataScope.none();
            };
        }
    }
}
