package com.acme.order.infrastructure.converter;

import com.acme.order.business.model.Order;
import com.acme.order.infrastructure.orm.po.OrderPO;
import com.ddk.core.mapper.BeanMapper;
import com.ddk.core.mapper.EnhancedMapper;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 订单实体转持久化对象。
 */
@Component
@EnhancedMapper(source = Order.class, target = OrderPO.class)
public class OrderPoConverter implements BeanMapper<Order, OrderPO> {

    @Override
    public OrderPO map(Order source) {
        OrderPO po = new OrderPO();
        po.setId(source.id().value());
        po.setCustomerId(source.customerId());
        po.setAmount(source.amount());
        po.setStatus(source.status().name());
        po.setVersion(source.version());
        return po;
    }

    @Override
    public List<OrderPO> map(List<Order> sources) {
        return sources.stream().map(this::map).toList();
    }
}
