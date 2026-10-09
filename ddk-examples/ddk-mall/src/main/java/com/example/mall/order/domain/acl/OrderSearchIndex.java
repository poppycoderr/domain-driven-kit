package com.example.mall.order.domain.acl;

import com.ddk.core.page.PageQuery;
import com.ddk.core.page.PageResponse;
import com.example.mall.order.domain.model.OrderId;
import com.example.mall.order.domain.model.OrderStatus;
import com.example.mall.order.domain.model.OrderSummary;

/**
 * 订单搜索用的读模型。它和订单表是两份数据：订单表为写入和一致性而设计，这里为「按商品名搜、按状态筛、一行展示」而设计。
 * <p>
 * 现在的实现是关系库里的一张宽表；换成搜索引擎时只换实现，订单上下文的其余部分不变。
 */
public interface OrderSearchIndex {

    /**
     * 写入或覆盖一个订单的摘要。
     */
    void save(OrderSummary summary);

    void remove(OrderId orderId);

    /**
     * 一个顾客的订单，最新的在前。
     *
     * @param keyword 匹配商品名称，为空时不限
     * @param status  为空时不限
     */
    PageResponse<OrderSummary> search(Long customerId, String keyword, OrderStatus status, PageQuery page);
}
