package com.acme.order.application.service;

import com.acme.order.application.command.PlaceOrderCommand;
import com.acme.order.application.response.OrderResponse;
import com.acme.order.domain.acl.OrderIdGenerator;
import com.acme.order.domain.acl.OrderRepository;
import com.acme.order.domain.error.OrderError;
import com.acme.order.domain.model.Order;
import com.acme.order.domain.model.OrderId;
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

    @Transactional(readOnly = true)
    public String status(long id) {
        return load(id).status().name();
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
