package com.example.mall.order.infrastructure.converter;

import com.ddk.core.mapper.EnhancedMapper;
import com.ddk.core.mapper.ObjectMapper;
import com.example.mall.order.domain.model.Order;
import com.example.mall.order.domain.model.OrderLine;
import com.example.mall.order.infrastructure.orm.po.OrderLinePO;
import com.example.mall.order.infrastructure.orm.po.OrderPO;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 订单聚合转持久化对象，订单行一并转换并按顺序编号。
 */
@Component
@EnhancedMapper(source = Order.class, target = OrderPO.class, description = "Order -> OrderPO")
public class OrderPoConverter implements ObjectMapper<Order, OrderPO> {

    @Override
    public OrderPO map(Order source) {
        OrderPO po = new OrderPO();
        po.setId(source.id().value());
        po.setCustomerId(source.customerId());
        po.setStatus(source.status().name());
        po.setTotalAmount(source.totalAmount().amount());
        po.setCancelReason(source.cancelReason());
        po.setVersion(source.version());
        List<OrderLinePO> lines = new ArrayList<>();
        for (OrderLine line : source.lines()) {
            OrderLinePO linePo = new OrderLinePO();
            linePo.setOrderId(source.id().value());
            linePo.setLineNo(lines.size() + 1);
            linePo.setSkuId(line.skuId());
            linePo.setProductName(line.productName());
            linePo.setUnitPrice(line.unitPrice().amount());
            linePo.setQuantity(line.quantity());
            lines.add(linePo);
        }
        po.setLines(lines);
        return po;
    }

    @Override
    public List<OrderPO> map(List<Order> sources) {
        return sources.stream().map(this::map).toList();
    }
}
