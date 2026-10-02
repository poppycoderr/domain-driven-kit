package com.example.mall.order.adapter.controller;

import com.ddk.core.page.PageResponse;
import com.ddk.core.response.ApiResponse;
import com.example.mall.order.adapter.controller.request.CancelOrderRequest;
import com.example.mall.order.adapter.controller.request.PlaceOrderRequest;
import com.example.mall.order.application.query.OrderPageQuery;
import com.example.mall.order.application.response.OrderResponse;
import com.example.mall.order.application.service.OrderService;
import com.example.mall.platform.CustomerIdentity;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 订单接口。顾客身份来自请求头，顾客只能操作自己的订单。
 */
@RestController
@RequestMapping("/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    @PostMapping
    public ApiResponse<OrderResponse> place(@Valid @RequestBody PlaceOrderRequest request) {
        return ApiResponse.ofSuccess(orderService.place(request.toCommand(CustomerIdentity.currentCustomerId())));
    }

    @GetMapping("/{id}")
    public ApiResponse<OrderResponse> get(@PathVariable("id") Long id) {
        return ApiResponse.ofSuccess(orderService.get(CustomerIdentity.currentCustomerId(), id));
    }

    @PostMapping("/page")
    public ApiResponse<PageResponse<OrderResponse>> page(@Valid @RequestBody OrderPageQuery query) {
        return ApiResponse.ofSuccess(orderService.page(CustomerIdentity.currentCustomerId(), query));
    }

    @PostMapping("/{id}/cancel")
    public ApiResponse<OrderResponse> cancel(@PathVariable("id") Long id, @Valid @RequestBody CancelOrderRequest request) {
        return ApiResponse.ofSuccess(orderService.cancel(CustomerIdentity.currentCustomerId(), id, request.reason()));
    }
}
