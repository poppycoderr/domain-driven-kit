package com.ddk.archguard.fixture.four.valid.application;

import com.ddk.archguard.fixture.four.valid.domain.Order;
import com.ddk.archguard.fixture.four.valid.domain.OrderRepository;

public class OrderService {

    private final OrderRepository repository;

    public OrderService(OrderRepository repository) {
        this.repository = repository;
    }

    public Order get(long id) {
        return repository.find(id);
    }
}
