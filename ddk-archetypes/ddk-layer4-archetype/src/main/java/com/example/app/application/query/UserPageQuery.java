package com.example.app.application.query;

import com.ddk.core.page.PageQuery;
import com.ddk.mybatis.query.Operator;
import com.ddk.mybatis.query.Query;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

/**
 * 用户分页查询。
 * <p>
 * {@code @Query} 的 value 不写时取字段名的下划线形式作为列名，
 * 这里显式写出列名，避免驼峰转换和真实表结构不一致。
 *
 * @author Elijah Du
 * @date 2025/2/19
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class UserPageQuery extends PageQuery {

    @Query(value = "username", operator = Operator.LIKE)
    private String username;

    @Query(value = "gender", operator = Operator.IN)
    private List<Integer> genders;

    @Query(value = "status")
    private Boolean status;
}
