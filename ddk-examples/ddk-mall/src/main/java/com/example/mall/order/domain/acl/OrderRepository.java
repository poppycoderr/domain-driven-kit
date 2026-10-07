package com.example.mall.order.domain.acl;

import com.ddk.core.repository.GenericRepository;
import com.example.mall.order.domain.model.Order;
import com.example.mall.order.domain.model.OrderId;

import java.time.Instant;
import java.util.List;

/**
 * 订单仓储。订单连同订单行作为一个整体保存和加载。
 */
public interface OrderRepository extends GenericRepository<Order, OrderId> {

    /**
     * 还在进行中、支付期限已到的订单，最早到期的在前，最多返回 {@code limit} 个。
     */
    List<OrderId> findExpired(Instant now, int limit);
}
