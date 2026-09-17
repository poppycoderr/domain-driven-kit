package com.ddk.archguard.fixture.four.valid.adapter;

import com.ddk.archguard.fixture.four.valid.application.OrderService;

public class OrderController {

    private final OrderService service;

    public OrderController(OrderService service) {
        this.service = service;
    }

    public Object get(long id) {
        return service.get(id);
    }
}
