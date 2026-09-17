package com.ddk.archguard.fixture.four.valid.infrastructure;

import com.ddk.archguard.fixture.four.valid.domain.Order;
import com.ddk.archguard.fixture.four.valid.domain.OrderRepository;

public class OrderRepositoryImpl implements OrderRepository {

    @Override
    public Order find(long id) {
        return new Order();
    }
}
