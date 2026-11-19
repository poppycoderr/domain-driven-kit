package com.acme.order.business.service;

import com.acme.order.business.command.PlaceOrderCommand;
import com.acme.order.business.response.OrderResponse;
import com.acme.order.business.acl.OrderIdGenerator;
import com.acme.order.business.acl.OrderRepository;
import com.acme.order.business.error.OrderError;
import com.acme.order.business.model.Order;
import com.acme.order.business.model.OrderId;
import com.ddk.core.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 订单用例：下单、查询、取消。
 */
@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orders;
    private final OrderIdGenerator idGenerator;

    @Transactional
    public OrderResponse place(PlaceOrderCommand command) {
        return OrderResponse.from(orders.create(Order.place(idGenerator.nextId(), command.customerId(), command.amount())));
    }

    @Transactional(readOnly = true)
    public OrderResponse get(long id) {
        return OrderResponse.from(load(id));
    }

    @Transactional
    public OrderResponse cancel(long id) {
        Order order = load(id);
        order.cancel();
        return OrderResponse.from(orders.update(order));
    }

    private Order load(long id) {
        return orders.find(OrderId.of(id)).orElseThrow(() -> new BusinessException(OrderError.ORDER_NOT_FOUND, id));
    }
}
