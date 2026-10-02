package com.example.mall.order.domain.acl;

import com.example.mall.order.domain.model.OrderId;

/**
 * 订单标识生成器。标识在下单前生成，订单从诞生起就有身份，下单事件可以直接带上订单 ID。
 */
public interface OrderIdGenerator {

    OrderId nextId();
}
