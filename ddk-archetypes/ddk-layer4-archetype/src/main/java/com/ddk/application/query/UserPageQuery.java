package com.ddk.application.query;

import com.ddk.core.page.Operator;
import com.ddk.core.page.PageQuery;
import com.ddk.core.page.Query;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

/**
 * @author Elijah Du
 * @date 2025/2/19
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class UserPageQuery extends PageQuery {

    @Query(operator = Operator.LIKE)
    private String userName;

    @Query(value = "gender", operator = Operator.IN)
    private List<Integer> genders;
}
