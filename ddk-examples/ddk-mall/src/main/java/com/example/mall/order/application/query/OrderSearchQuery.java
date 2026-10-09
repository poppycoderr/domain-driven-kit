package com.example.mall.order.application.query;

import com.ddk.core.page.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 订单搜索条件。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class OrderSearchQuery extends PageQuery {

    /**
     * 匹配商品名称，不填则不限。
     */
    private String keyword;

    /**
     * 订单状态，不填则不限。
     */
    private String status;
}
