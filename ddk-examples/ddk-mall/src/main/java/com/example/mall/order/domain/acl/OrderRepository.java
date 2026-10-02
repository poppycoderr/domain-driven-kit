package com.example.mall.order.domain.acl;

import com.ddk.core.repository.GenericRepository;
import com.example.mall.order.domain.model.Order;
import com.example.mall.order.domain.model.OrderId;

/**
 * 订单仓储。订单连同订单行作为一个整体保存和加载。
 */
public interface OrderRepository extends GenericRepository<Order, OrderId> {
}
