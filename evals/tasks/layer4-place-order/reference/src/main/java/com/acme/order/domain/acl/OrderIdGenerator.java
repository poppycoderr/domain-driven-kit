package com.acme.order.domain.acl;

import com.acme.order.domain.model.OrderId;

/**
 * 订单标识生成端口：应用服务在创建订单之前取号。
 */
public interface OrderIdGenerator {

    OrderId nextId();
}
