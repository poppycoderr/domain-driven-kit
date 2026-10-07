package com.example.mall.order.infrastructure.converter;

import com.ddk.core.mapper.EnhancedMapper;
import com.ddk.core.mapper.ObjectMapper;
import com.example.mall.order.domain.model.Money;
import com.example.mall.order.domain.model.Order;
import com.example.mall.order.domain.model.OrderId;
import com.example.mall.order.domain.model.OrderLine;
import com.example.mall.order.domain.model.OrderStatus;
import com.example.mall.order.infrastructure.orm.po.OrderPO;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 持久化对象转订单聚合，经由 {@link Order#restore} 重建，不产生领域事件。
 */
@Component
@EnhancedMapper(source = OrderPO.class, target = Order.class, description = "OrderPO -> Order")
public class OrderEntityConverter implements ObjectMapper<OrderPO, Order> {

    @Override
    public Order map(OrderPO source) {
        List<OrderLine> lines = source.getLines().stream()
                .map(line -> new OrderLine(line.getSkuId(), line.getProductName(), new Money(line.getUnitPrice()), line.getQuantity()))
                .toList();
        return Order.restore(OrderId.of(source.getId()), source.getCustomerId(), lines, OrderStatus.valueOf(source.getStatus()),
                new Money(source.getTotalAmount()), source.getCancelReason(), source.getExpiresAt(), source.getVersion());
    }

    @Override
    public List<Order> map(List<OrderPO> sources) {
        return sources.stream().map(this::map).toList();
    }
}
