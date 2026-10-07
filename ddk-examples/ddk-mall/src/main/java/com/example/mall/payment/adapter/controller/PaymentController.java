package com.example.mall.payment.adapter.controller;

import com.ddk.core.response.ApiResponse;
import com.example.mall.payment.application.response.PaymentResponse;
import com.example.mall.payment.application.service.PaymentService;
import com.example.mall.platform.CustomerIdentity;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 支付接口，按订单号查询和支付。顾客只能支付自己的订单。
 */
@RestController
@RequestMapping("/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    @GetMapping("/orders/{orderId}")
    public ApiResponse<PaymentResponse> get(@PathVariable("orderId") Long orderId) {
        return ApiResponse.ofSuccess(paymentService.get(CustomerIdentity.currentCustomerId(), orderId));
    }

    @PostMapping("/orders/{orderId}/pay")
    public ApiResponse<PaymentResponse> pay(@PathVariable("orderId") Long orderId) {
        return ApiResponse.ofSuccess(paymentService.pay(CustomerIdentity.currentCustomerId(), orderId));
    }
}
