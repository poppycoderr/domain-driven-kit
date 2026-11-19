package com.acme.order.infrastructure.converter;

import com.acme.order.business.model.Order;
import com.acme.order.business.model.OrderId;
import com.acme.order.business.model.OrderStatus;
import com.acme.order.infrastructure.orm.po.OrderPO;
import com.ddk.core.mapper.BeanMapper;
import com.ddk.core.mapper.EnhancedMapper;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 持久化对象转订单实体，经由 {@link Order#restore} 重建。
 */
@Component
@EnhancedMapper(source = OrderPO.class, target = Order.class)
public class OrderEntityConverter implements BeanMapper<OrderPO, Order> {

    @Override
    public Order map(OrderPO source) {
        return Order.restore(OrderId.of(source.getId()), source.getCustomerId(), source.getAmount(),
                OrderStatus.valueOf(source.getStatus()), source.getVersion());
    }

    @Override
    public List<Order> map(List<OrderPO> sources) {
        return sources.stream().map(this::map).toList();
    }
}
