package com.acme.order.infrastructure.acl.impl;

import com.acme.order.domain.acl.OrderRepository;
import com.acme.order.domain.model.Order;
import com.acme.order.domain.model.OrderId;
import com.acme.order.infrastructure.orm.mapper.OrderMapper;
import com.acme.order.infrastructure.orm.po.OrderPO;
import com.ddk.mybatis.repository.GenericRepositoryImpl;
import org.springframework.stereotype.Repository;

/**
 * 订单仓储实现。
 */
@Repository
public class OrderRepositoryImpl extends GenericRepositoryImpl<Order, OrderId, OrderPO, OrderMapper> implements OrderRepository {
}
