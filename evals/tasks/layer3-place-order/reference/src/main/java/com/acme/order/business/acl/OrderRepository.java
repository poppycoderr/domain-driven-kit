package com.acme.order.business.acl;

import com.acme.order.business.model.Order;
import com.acme.order.business.model.OrderId;
import com.ddk.core.repository.GenericRepository;

/**
 * 订单仓储契约。
 */
public interface OrderRepository extends GenericRepository<Order, OrderId> {
}
