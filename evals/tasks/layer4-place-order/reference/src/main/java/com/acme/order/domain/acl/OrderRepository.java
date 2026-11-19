package com.acme.order.domain.acl;

import com.acme.order.domain.model.Order;
import com.acme.order.domain.model.OrderId;
import com.ddk.core.repository.GenericRepository;

/**
 * 订单仓储契约。
 */
public interface OrderRepository extends GenericRepository<Order, OrderId> {
}
