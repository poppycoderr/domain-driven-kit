package com.example.user.application.query;

import com.ddk.core.page.PageQuery;
import com.ddk.mybatis.query.Operator;
import com.ddk.mybatis.query.Query;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

/**
 * 用户分页查询条件，字段上的 {@link Query} 由通用仓储翻译成 SQL 条件。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class UserPageQuery extends PageQuery {

    @Query(value = "username", operator = Operator.LIKE)
    private String username;

    @Query(value = "gender", operator = Operator.IN)
    private List<Integer> genders;

    @Query(value = "status")
    private Boolean enabled;
}
