package com.ddk.mybatis.query;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.ddk.core.page.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

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
}
