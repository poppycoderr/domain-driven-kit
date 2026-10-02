package com.example.mall.order.infrastructure.acl.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.ddk.mybatis.repository.GenericRepositoryImpl;
import com.example.mall.order.domain.acl.OrderRepository;
import com.example.mall.order.domain.model.Order;
import com.example.mall.order.domain.model.OrderId;
import com.example.mall.order.infrastructure.orm.mapper.OrderLineMapper;
import com.example.mall.order.infrastructure.orm.mapper.OrderMapper;
import com.example.mall.order.infrastructure.orm.po.OrderLinePO;
import com.example.mall.order.infrastructure.orm.po.OrderPO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.io.Serializable;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 订单仓储实现。根表的读写、乐观锁与事件发布由 {@link GenericRepositoryImpl} 完成，这里只处理订单行表。
 * 订单行下单后不再变化，所以更新订单时不需要同步订单行。
 */
@Repository
@RequiredArgsConstructor
public class OrderRepositoryImpl extends GenericRepositoryImpl<Order, OrderId, OrderPO, OrderMapper> implements OrderRepository {

    private final OrderLineMapper orderLineMapper;

    @Override
    protected void afterInsert(OrderPO po) {
        po.getLines().forEach(orderLineMapper::insert);
    }

    @Override
    protected void afterLoad(List<OrderPO> pos) {
        if (pos.isEmpty()) {
            return;
        }
        Map<Long, List<OrderLinePO>> linesByOrder = orderLineMapper.selectList(Wrappers.lambdaQuery(OrderLinePO.class)
                        .in(OrderLinePO::getOrderId, pos.stream().map(OrderPO::getId).toList())
                        .orderByAsc(OrderLinePO::getLineNo))
                .stream().collect(Collectors.groupingBy(OrderLinePO::getOrderId));
        pos.forEach(po -> po.setLines(linesByOrder.getOrDefault(po.getId(), List.of())));
    }

    @Override
    protected void beforeRemove(List<Serializable> keys) {
        orderLineMapper.delete(Wrappers.lambdaQuery(OrderLinePO.class).in(OrderLinePO::getOrderId, keys));
    }
}
