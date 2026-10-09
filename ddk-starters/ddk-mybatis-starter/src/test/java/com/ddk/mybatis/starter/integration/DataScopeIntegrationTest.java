package com.ddk.mybatis.starter.integration;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ddk.core.context.DataScope;
import com.ddk.core.context.Operator;
import com.ddk.core.context.OperatorContext;
import com.ddk.mybatis.starter.config.MybatisPlusAutoConfiguration;
import com.ddk.mybatis.starter.datascope.DataScopeResolver;
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

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 对真实执行的 SQL 验证：受控表的查询、更新、删除都被收窄到操作者的数据范围之内，取不到操作者时语句失败。
 */
@SpringBootTest(classes = DataScopeIntegrationTest.TestApp.class, properties = {
        "spring.datasource.url=jdbc:h2:mem:datascope;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.sql.init.mode=always",
        "spring.sql.init.schema-locations=classpath:data-scope-schema.sql",
        "ddk.mybatis.db-type=h2",
        "ddk.mybatis.data-scope.enabled=true",
        "ddk.mybatis.data-scope.tables=T_CONTRACT,t_note",
        "ddk.mybatis.data-scope.columns.t_note.group-column=",
        "ddk.mybatis.data-scope.columns.t_note.owner-column=author",
        "ddk.mybatis.data-scope.columns.t_note.numeric-owner-id=false"
})
@DisplayName("行级数据权限（H2）")
class DataScopeIntegrationTest {

    /** 销售一部的主管：看得到本部门（10）的数据 */
    private static final Operator MANAGER = Operator.of("1");

    /** 销售一部的员工：只看得到自己创建的 */
    private static final Operator CLERK = Operator.of("2");

    /** 区域负责人：两个部门（10、20），外加自己创建的 */
    private static final Operator REGIONAL = Operator.of("3");

    private static final Operator ADMIN = Operator.of("9");

    /** 没有分配任何范围 */
    private static final Operator GUEST = Operator.of("7");

    private static final Map<String, DataScope> SCOPES = Map.of(
            "1", DataScope.ofGroups(List.of("10")),
            "2", DataScope.ownOnly(),
            "3", DataScope.ofGroups(List.of("10", "20")).andOwn(),
            "9", DataScope.all());

    @Autowired
    private ContractMapper contracts;

    @Autowired
    private RegionMapper regions;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void seed() {
        jdbc.update("DELETE FROM t_contract");
        jdbc.update("DELETE FROM t_note");
        jdbc.update("DELETE FROM t_region");
        jdbc.update("INSERT INTO t_contract (id, title, dept_id, create_by) VALUES "
                + "(1, 'dept 10 by manager', 10, 1), (2, 'dept 10 by clerk', 10, 2), (3, 'dept 20 by someone', 20, 5), "
                + "(4, 'dept 30 by regional', 30, 3), (5, 'dept 30 by someone', 30, 5)");
        jdbc.update("INSERT INTO t_note (id, title, author) VALUES (1, 'mine', '2'), (2, 'o''brien''s', 'o''brien')");
        jdbc.update("INSERT INTO t_region (id, name) VALUES (1, 'east')");
    }

    @Test
    @DisplayName("查询按范围收窄：部门、本人、两者取或、全部、什么都没有")
    void queriesAreNarrowedToTheScope() {
        assertThat(ids(MANAGER)).containsExactly(1L, 2L);
        assertThat(ids(CLERK)).containsExactly(2L);
        assertThat(ids(REGIONAL)).containsExactly(1L, 2L, 3L, 4L);
        assertThat(ids(ADMIN)).containsExactly(1L, 2L, 3L, 4L, 5L);
        assertThat(ids(GUEST)).isEmpty();
    }

    @Test
    @DisplayName("按主键读取、分页计数、带别名的联表查询同样受限")
    void lookupsCountsAndJoinsAreScopedToo() {
        OperatorContext.runAs(CLERK, () -> {
            assertThat(contracts.selectById(1L)).isNull();
            assertThat(contracts.selectById(2L)).isNotNull();
            Page<ContractPo> page = contracts.selectPage(new Page<>(1, 10), null);
            assertThat(page.getTotal()).isEqualTo(1);
            assertThat(contracts.titlesWithAlias()).containsExactly("dept 10 by clerk");
        });
    }

    @Test
    @DisplayName("范围之外的数据改不了也删不掉")
    void rowsOutsideTheScopeCannotBeChangedOrDeleted() {
        OperatorContext.runAs(MANAGER, () -> {
            ContractPo foreign = new ContractPo();
            foreign.id = 3L;
            foreign.title = "hijacked";
            assertThat(contracts.updateById(foreign)).isZero();
            assertThat(contracts.deleteById(3L)).isZero();

            ContractPo own = contracts.selectById(1L);
            own.title = "renamed";
            assertThat(contracts.updateById(own)).isEqualTo(1);
        });

        assertThat(jdbc.queryForObject("SELECT title FROM t_contract WHERE id = 3", String.class)).isEqualTo("dept 20 by someone");
        assertThat(jdbc.queryForObject("SELECT title FROM t_contract WHERE id = 1", String.class)).isEqualTo("renamed");
    }

    @Test
    @DisplayName("没有操作者时语句失败，而不是读到全部数据；没有列入的表不受影响，显式豁免的语句不加条件")
    void failsClosedAndLeavesOtherTablesAlone() {
        assertThatThrownBy(() -> contracts.selectList(null))
                .hasMessageContaining("Data scope is enabled for table t_contract")
                .hasMessageContaining("OperatorContext has no operator");

        assertThat(regions.selectList(null)).hasSize(1);
        assertThat(contracts.countAll()).isEqualTo(5);
    }

    @Test
    @DisplayName("个别表可以换列名、去掉一个维度；字符串标识里的单引号被转义")
    void perTableColumnsAndStringIdentifiers() {
        assertThat(OperatorContext.callAs(CLERK, () -> contracts.noteTitles())).containsExactly("mine");
        assertThat(OperatorContext.callAs(MANAGER, () -> contracts.noteTitles()))
                .as("t_note has no group column, so a scope made of groups only sees nothing").isEmpty();
        assertThat(OperatorContext.callAs(Operator.of("o'brien"), () -> contracts.noteTitles())).containsExactly("o'brien's");
    }

    private List<Long> ids(Operator operator) {
        return OperatorContext.callAs(operator, () -> contracts.selectList(null).stream().map(po -> po.id).sorted().toList());
    }

    @TableName("t_contract")
    static class ContractPo {

        @TableId
        Long id;

        String title;

        Long deptId;

        Long createBy;
    }

    @TableName("t_region")
    static class RegionPo {

        @TableId
        Long id;

        String name;
    }

    @Mapper
    interface ContractMapper extends BaseMapper<ContractPo> {

        @Select("SELECT c.title FROM t_contract c LEFT JOIN t_region r ON r.id = 1 ORDER BY c.id")
        List<String> titlesWithAlias();

        @Select("SELECT title FROM t_note ORDER BY id")
        List<String> noteTitles();

        @InterceptorIgnore(dataPermission = "true")
        @Select("SELECT COUNT(*) FROM t_contract")
        int countAll();
    }

    @Mapper
    interface RegionMapper extends BaseMapper<RegionPo> {
    }

    @Configuration
    @MapperScan(basePackageClasses = DataScopeIntegrationTest.class)
    @ImportAutoConfiguration({
            DataSourceAutoConfiguration.class,
            DataSourceInitializationAutoConfiguration.class,
            JdbcTemplateAutoConfiguration.class,
            MybatisPlusAutoConfiguration.class,
            com.baomidou.mybatisplus.autoconfigure.MybatisPlusAutoConfiguration.class,
    })
    static class TestApp {

        /**
         * 真实项目里从角色与组织关系算出范围并缓存；字符串标识的操作者（o'brien）只看自己的。
         */
        @Bean
        DataScopeResolver dataScopeResolver() {
            return operator -> operator.id().matches("\\d+") ? SCOPES.getOrDefault(operator.id(), DataScope.none()) : DataScope.ownOnly();
        }
    }
}
