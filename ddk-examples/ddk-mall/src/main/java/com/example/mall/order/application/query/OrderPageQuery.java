package com.example.mall.order.application.query;

import com.ddk.core.page.PageQuery;
import com.ddk.mybatis.query.Query;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 订单分页查询条件。顾客只能查自己的订单，{@code customerId} 由应用服务填入，不从请求体里取。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class OrderPageQuery extends PageQuery {

    @Query(value = "customer_id")
    private Long customerId;

    @Query(value = "status")
    private String status;
}
