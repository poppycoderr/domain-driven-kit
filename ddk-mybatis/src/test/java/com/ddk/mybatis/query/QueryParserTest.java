package com.ddk.mybatis.query;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.ddk.core.exception.BusinessException;
import com.ddk.core.exception.CommonError;
import com.ddk.core.page.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("QueryParser：把查询对象解析成 QueryWrapper")
class QueryParserTest {

    @Data
    @EqualsAndHashCode(callSuper = true)
    static class UserQuery extends PageQuery {

        @Query(value = "username", operator = Operator.LIKE)
        private String username;

        @Query(operator = Operator.EQ)
        private Integer userStatus;

        @Query(value = "gender", operator = Operator.IN)
        private List<Integer> genders;

        @Query(value = "age", operator = Operator.BETWEEN)
        private List<Integer> ageRange;

        /** 没有 @Query，不应进入 SQL */
        private String ignored;
    }

    @TableName("t_user")
    static class UserPo {

        @TableId
        Long id;

        Integer userStatus;

        LocalDateTime createTime;

        @TableField("display_name")
        String nickname;

        @TableField(select = false)
        String passwordHash;
    }

    /** 没有初始化表信息的持久化对象，相当于没有对应 Mapper */
    static class UnmappedPo {
    }

    @BeforeAll
    static void registerTable() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), "test"), UserPo.class);
    }

    private static String sqlOf(QueryWrapper<?> wrapper) {
        return wrapper.getTargetSql();
    }

    @Test
    @DisplayName("只有标注了 @Query 的字段参与条件构建")
    void onlyAnnotatedFieldsBecomeConditions() {
        UserQuery query = new UserQuery();
        query.setUsername("ali");
        query.setIgnored("should-not-appear");

        String sql = sqlOf(QueryParser.parse(query));

        assertTrue(sql.contains("username"), "实际 SQL：" + sql);
        assertFalse(sql.contains("ignored"), "实际 SQL：" + sql);
    }

    @Test
    @DisplayName("value 缺省时列名取字段名的下划线形式")
    void columnFallsBackToUnderscoreFieldName() {
        UserQuery query = new UserQuery();
        query.setUserStatus(1);

        assertTrue(sqlOf(QueryParser.parse(query)).contains("user_status"));
    }

    @Test
    @DisplayName("null 值不生成条件，避免把 where x is null 当成筛选")
    void nullValuesAreSkipped() {
        String sql = sqlOf(QueryParser.parse(new UserQuery()));

        assertFalse(sql.contains("username"), "实际 SQL：" + sql);
        assertFalse(sql.contains("gender"), "实际 SQL：" + sql);
    }

    @Test
    @DisplayName("空集合不生成 IN 条件")
    void emptyCollectionIsSkipped() {
        UserQuery query = new UserQuery();
        query.setGenders(List.of());

        assertFalse(sqlOf(QueryParser.parse(query)).contains("gender"));
    }

    @Test
    @DisplayName("IN 与 BETWEEN 按集合内容展开")
    void collectionOperators() {
        UserQuery query = new UserQuery();
        query.setGenders(List.of(0, 1));
        query.setAgeRange(List.of(18, 60));

        String sql = sqlOf(QueryParser.parse(query));

        assertTrue(sql.contains("gender IN"), "实际 SQL：" + sql);
        assertTrue(sql.contains("age BETWEEN"), "实际 SQL：" + sql);
    }

    @Test
    @DisplayName("PageQuery.addSort 添加的排序会进入 ORDER BY，字段名转下划线")
    void sortsReachOrderBy() {
        UserQuery query = new UserQuery();
        query.addSort("createTime", "DESC").addSort("id", "ASC");

        String sql = sqlOf(QueryParser.parse(query));

        assertTrue(sql.contains("ORDER BY"), "实际 SQL：" + sql);
        assertTrue(sql.contains("create_time DESC"), "实际 SQL：" + sql);
        assertTrue(sql.contains("id ASC"), "实际 SQL：" + sql);
    }

    @Test
    @DisplayName("没有排序时不产生 ORDER BY")
    void noSortNoOrderBy() {
        assertFalse(sqlOf(QueryParser.parse(new UserQuery())).contains("ORDER BY"));
    }

    @Test
    @DisplayName("排序字段只接受标识符：带括号、空格、引号或注释的表达式不会进入 ORDER BY")
    void rejectsSortFieldsThatAreNotIdentifiers() {
        for (String field : List.of("(select 1 from no_such_table)", "id desc, (select 1)", "id;drop table t", "id--", "user.name", "id'")) {
            UserQuery query = new UserQuery();
            query.addSort(field, "ASC");

            BusinessException e = assertThrows(BusinessException.class, () -> QueryParser.parse(query), field);
            assertEquals(CommonError.VALIDATION_ERROR, e.getErrorCode(), field);
        }
    }

    @Test
    @DisplayName("空的排序字段被跳过，不产生 ORDER BY")
    void skipsEmptySortFields() {
        UserQuery query = new UserQuery();
        query.addSort("", "ASC");

        assertFalse(sqlOf(QueryParser.parse(query)).contains("ORDER BY"));
    }

    @Test
    @DisplayName("给出持久化对象时，排序字段按属性名或列名换成表里的列，改过名的列也能排序")
    void sortFieldsMapToTheColumnsOfThePo() {
        UserQuery query = new UserQuery();
        query.addSort("createTime", "DESC").addSort("nickname", "ASC").addSort("user_status", "ASC").addSort("id", "ASC");

        String sql = sqlOf(QueryParser.parse(query, UserPo.class));

        assertTrue(sql.contains("ORDER BY create_time DESC,display_name ASC,user_status ASC,id ASC"), "实际 SQL：" + sql);
    }

    @Test
    @DisplayName("不是这张表的列、或者不查询出来的列，不能用来排序")
    void sortFieldsOutsideThePoAreRejected() {
        for (String field : List.of("noSuchColumn", "passwordHash", "password_hash")) {
            UserQuery query = new UserQuery();
            query.addSort(field, "ASC");

            BusinessException e = assertThrows(BusinessException.class, () -> QueryParser.parse(query, UserPo.class), field);
            assertEquals(CommonError.VALIDATION_ERROR, e.getErrorCode(), field);
        }
    }

    @Test
    @DisplayName("持久化对象没有表信息时退回只检查标识符的规则")
    void withoutTableInfoOnlyIdentifiersAreChecked() {
        UserQuery query = new UserQuery();
        query.addSort("createTime", "DESC");

        assertTrue(sqlOf(QueryParser.parse(query, UnmappedPo.class)).contains("create_time DESC"));

        UserQuery injected = new UserQuery();
        injected.addSort("(select 1)", "ASC");
        assertThrows(BusinessException.class, () -> QueryParser.parse(injected, UnmappedPo.class));
    }
}
