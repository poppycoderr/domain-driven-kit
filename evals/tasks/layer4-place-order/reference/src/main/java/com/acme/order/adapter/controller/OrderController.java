package com.acme.order.adapter.controller;

import com.acme.order.adapter.controller.request.PlaceOrderRequest;
import com.acme.order.application.response.OrderResponse;
import com.acme.order.application.service.OrderService;
import com.ddk.core.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 订单接口。
 */
@RestController
@RequestMapping("/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    @PostMapping
    public ApiResponse<OrderResponse> place(@Valid @RequestBody PlaceOrderRequest request) {
        return ApiResponse.ofSuccess(orderService.place(request.toCommand()));
    }

    @GetMapping("/{id}")
    public ApiResponse<OrderResponse> get(@PathVariable("id") long id) {
        return ApiResponse.ofSuccess(orderService.get(id));
    }

    @PostMapping("/{id}/cancel")
    public ApiResponse<OrderResponse> cancel(@PathVariable("id") long id) {
        return ApiResponse.ofSuccess(orderService.cancel(id));
    }
}
