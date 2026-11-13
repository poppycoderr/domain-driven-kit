package com.ddk.mybatis.starter.integration;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ddk.core.context.DataScope;
import com.ddk.core.context.Operator;
import com.ddk.core.context.OperatorContext;
import com.ddk.mybatis.starter.config.DdkMybatisAutoConfiguration;
import com.ddk.mybatis.starter.datascope.DataScopeResolver;
import com.ddk.test.containers.DdkContainers;
import org.apache.ibatis.annotations.Mapper;
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
 * 字符串形式的部门和用户标识写进数据权限条件，在真实的 MySQL 上执行。MySQL 默认把反斜杠当转义符，
 * 只转义单引号时，一个以反斜杠结尾的标识能让下一个标识的内容变成 SQL。
 */
@Testcontainers(disabledWithoutDocker = true)
@DirtiesContext
@SpringBootTest(classes = DataScopeStringIdsMysqlIntegrationTest.TestApp.class, properties = {
        "spring.sql.init.mode=always",
        "spring.sql.init.schema-locations=classpath:tenant-data-scope-schema.sql",
        "ddk.mybatis.db-type=mysql",
        "ddk.mybatis.tenant.enabled=true",
        "ddk.mybatis.data-scope.enabled=true",
        "ddk.mybatis.data-scope.tables=t_deal",
        "ddk.mybatis.data-scope.numeric-group-id=false",
        "ddk.mybatis.data-scope.numeric-owner-id=false"
})
@DisplayName("行级数据权限：字符串标识（MySQL）")
class DataScopeStringIdsMysqlIntegrationTest {

    @Container
    static final MySQLContainer MYSQL = DdkContainers.mysql();

    @Autowired
    private StringIdDealMapper deals;

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
        jdbc.update("INSERT INTO t_deal (id, title, tenant_id, dept_id, create_by) VALUES "
                + "(1, 't1 sales', 1, 10, 1), (2, 't1 sales', 1, 10, 2), (3, 't1 support', 1, 20, 2), "
                + "(4, 't1 support', 1, 20, 5), (5, 't2 sales', 2, 10, 1), (6, 't2 sales', 2, 10, 2)");
    }

    @Test
    @DisplayName("字符串部门标识照常过滤，含单引号的标识按字面值比较")
    void stringGroupsFilter() {
        assertThat(ids(Operator.of("1", "1"))).containsExactly(1L, 2L);
        assertThat(ids(Operator.of("8", "1"))).as("a group with a quote matches nothing and does not fail").isEmpty();
    }

    @Test
    @DisplayName("以反斜杠结尾的部门标识被拒绝，后一个标识的内容不会变成 SQL")
    void backslashInGroupIsRejected() {
        assertThatThrownBy(() -> ids(Operator.of("7", "1"))).hasStackTraceContaining("cannot be used in SQL");
    }

    @Test
    @DisplayName("含反斜杠或控制字符的用户标识被拒绝")
    void backslashInOwnerIsRejected() {
        assertThatThrownBy(() -> ids(Operator.of("2\\", "1"))).hasStackTraceContaining("cannot be used in SQL");
        assertThatThrownBy(() -> ids(Operator.of("2\u0000", "1"))).hasStackTraceContaining("cannot be used in SQL");
    }

    private List<Long> ids(Operator operator) {
        return OperatorContext.callAs(operator, () -> deals.selectList(null).stream().map(po -> po.id).sorted().toList());
    }

    @TableName("t_deal")
    static class StringIdDealPo {

        @TableId
        Long id;

        String title;
    }

    @Mapper
    interface StringIdDealMapper extends BaseMapper<StringIdDealPo> {
    }

    @Configuration
    @MapperScan(basePackageClasses = DataScopeStringIdsMysqlIntegrationTest.class, annotationClass = Mapper.class)
    @ImportAutoConfiguration({
            DataSourceAutoConfiguration.class,
            DataSourceInitializationAutoConfiguration.class,
            JdbcTemplateAutoConfiguration.class,
            DdkMybatisAutoConfiguration.class,
            com.baomidou.mybatisplus.autoconfigure.MybatisPlusAutoConfiguration.class,
    })
    static class TestApp {

        @Bean
        DataScopeResolver dataScopeResolver() {
            return operator -> switch (operator.id()) {
                case "1" -> DataScope.ofGroups(List.of("10"));
                case "7" -> DataScope.ofGroups(List.of("\\", "^0)) OR 1=1 -- "));
                case "8" -> DataScope.ofGroups(List.of("o'neil"));
                default -> DataScope.ownOnly();
            };
        }
    }
}
