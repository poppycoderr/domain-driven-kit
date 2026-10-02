package com.ddk.mybatis.starter.integration;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ddk.core.context.Operator;
import com.ddk.core.context.OperatorContext;
import com.ddk.mybatis.starter.config.MybatisPlusAutoConfiguration;
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
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 对真实执行的 SQL 验证：租户条件确实加上了，取不到租户时语句失败，审计字段写的是当前操作者。
 */
@SpringBootTest(classes = TenantAndAuditIntegrationTest.TestApp.class, properties = {
        "spring.datasource.url=jdbc:h2:mem:tenant;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.sql.init.mode=always",
        "spring.sql.init.schema-locations=classpath:tenant-schema.sql",
        "ddk.mybatis.db-type=h2",
        "ddk.mybatis.tenant.enabled=true",
        "ddk.mybatis.tenant.ignore-tables=T_DICT"
})
@DisplayName("多租户与审计字段（H2）")
class TenantAndAuditIntegrationTest {

    private static final Operator ALICE = Operator.of("alice", "1");

    private static final Operator BOB = Operator.of("bob", "2");

    @Autowired
    private ArticleMapper articles;

    @Autowired
    private DictMapper dicts;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void clean() {
        jdbc.update("DELETE FROM t_article");
        jdbc.update("DELETE FROM t_dict");
    }

    @Test
    @DisplayName("写入时带上租户，查询、更新、删除都只作用于自己租户的数据")
    void statementsAreScopedToTheTenant() {
        OperatorContext.runAs(ALICE, () -> articles.insert(article(1L, "alice's")));
        OperatorContext.runAs(BOB, () -> articles.insert(article(2L, "bob's")));

        assertThat(jdbc.queryForObject("SELECT tenant_id FROM t_article WHERE id = 1", Long.class)).isEqualTo(1L);
        OperatorContext.runAs(ALICE, () -> {
            assertThat(articles.selectList(null)).extracting(a -> a.title).containsExactly("alice's");
            assertThat(articles.selectById(2L)).isNull();

            ArticlePo foreign = article(2L, "hijacked");
            assertThat(articles.updateById(foreign)).isZero();
            assertThat(articles.deleteById(2L)).isZero();
        });
        assertThat(jdbc.queryForObject("SELECT title FROM t_article WHERE id = 2", String.class)).isEqualTo("bob's");
    }

    @Test
    @DisplayName("取不到租户时语句失败，而不是读到所有租户的数据")
    void failsClosedWithoutTenant() {
        assertThatThrownBy(() -> articles.selectList(null)).hasMessageContaining("OperatorContext has no tenant");
        assertThatThrownBy(() -> OperatorContext.runAs(Operator.of("system"), () -> articles.selectList(null)))
                .hasMessageContaining("OperatorContext has no tenant");
    }

    @Test
    @DisplayName("忽略的表不加租户条件，显式豁免的语句可以跨租户")
    void ignoredTablesAndExemptedStatements() {
        DictPo dict = new DictPo();
        dict.id = 1L;
        dict.label = "gender";
        dicts.insert(dict);
        assertThat(dicts.selectList(null)).hasSize(1);

        OperatorContext.runAs(ALICE, () -> articles.insert(article(1L, "alice's")));
        OperatorContext.runAs(BOB, () -> articles.insert(article(2L, "bob's")));
        assertThat(articles.countAllTenants()).isEqualTo(2);
    }

    @Test
    @DisplayName("创建人只在插入时写一次，修改人随每次更新变化")
    void auditFieldsFollowTheOperator() {
        OperatorContext.runAs(ALICE, () -> articles.insert(article(1L, "draft")));
        OperatorContext.runAs(new Operator("carol", null, "1"), () -> {
            ArticlePo po = articles.selectById(1L);
            po.title = "published";
            articles.updateById(po);
        });

        List<String> audit = jdbc.queryForObject("SELECT create_by || ',' || update_by FROM t_article WHERE id = 1",
                (rs, row) -> List.of(rs.getString(1).split(",")));
        assertThat(audit).containsExactly("alice", "carol");
    }

    private static ArticlePo article(Long id, String title) {
        ArticlePo po = new ArticlePo();
        po.id = id;
        po.title = title;
        return po;
    }

    @TableName("t_article")
    static class ArticlePo {

        @TableId
        Long id;

        String title;

        @TableField(fill = FieldFill.INSERT)
        String createBy;

        @TableField(fill = FieldFill.INSERT_UPDATE)
        String updateBy;

        @TableField(fill = FieldFill.INSERT)
        LocalDateTime createTime;

        @TableField(fill = FieldFill.INSERT_UPDATE)
        LocalDateTime updateTime;
    }

    @TableName("t_dict")
    static class DictPo {

        @TableId
        Long id;

        String label;
    }

    @Mapper
    interface ArticleMapper extends BaseMapper<ArticlePo> {

        @InterceptorIgnore(tenantLine = "true")
        @Select("SELECT COUNT(*) FROM t_article")
        int countAllTenants();
    }

    @Mapper
    interface DictMapper extends BaseMapper<DictPo> {
    }

    @Configuration
    @MapperScan(basePackageClasses = TenantAndAuditIntegrationTest.class)
    @ImportAutoConfiguration({
            DataSourceAutoConfiguration.class,
            DataSourceInitializationAutoConfiguration.class,
            JdbcTemplateAutoConfiguration.class,
            MybatisPlusAutoConfiguration.class,
            com.baomidou.mybatisplus.autoconfigure.MybatisPlusAutoConfiguration.class,
    })
    static class TestApp {
    }
}
