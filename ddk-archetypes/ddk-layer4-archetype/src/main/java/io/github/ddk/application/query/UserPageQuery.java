package io.github.ddk.application.query;

import io.github.ddk.core.page.Operator;
import io.github.ddk.core.page.PageQuery;
import io.github.ddk.core.page.Query;
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
