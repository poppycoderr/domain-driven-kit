package com.ddk.archguard.fixture.configonly.valid.infrastructure;

import com.ddk.archguard.fixture.configonly.valid.infrastructure.orm.mapper.OrderMapper;

public class OrderRepositoryImpl {

    private final OrderMapper orders;

    public OrderRepositoryImpl(OrderMapper orders) {
        this.orders = orders;
    }

    public int count() {
        return orders.countAll();
    }
}
