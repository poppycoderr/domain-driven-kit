package com.example.mall.order.adapter.controller;

import com.ddk.core.page.PageResponse;
import com.ddk.core.response.ApiResponse;
import com.example.mall.order.adapter.controller.request.CancelOrderRequest;
import com.example.mall.order.adapter.controller.request.PlaceOrderRequest;
import com.example.mall.order.application.query.OrderPageQuery;
import com.example.mall.order.application.query.OrderSearchQuery;
import com.example.mall.order.application.response.OrderResponse;
import com.example.mall.order.application.response.OrderSummaryResponse;
import com.example.mall.order.application.response.SearchIndexStatusResponse;
import com.example.mall.order.application.service.OrderSearchService;
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

    private final OrderSearchService orderSearchService;

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

    /**
     * 按商品名称和状态搜索自己的订单。查的是读模型，刚发生的变化可能要过一小会儿才反映出来。
     */
    @PostMapping("/search")
    public ApiResponse<PageResponse<OrderSummaryResponse>> search(@Valid @RequestBody OrderSearchQuery query) {
        return ApiResponse.ofSuccess(orderSearchService.search(CustomerIdentity.currentCustomerId(), query));
    }

    /**
     * 运营操作：重建订单搜索的读模型。
     */
    @PostMapping("/search/rebuild")
    public ApiResponse<SearchIndexStatusResponse> rebuildSearch() {
        return ApiResponse.ofSuccess(orderSearchService.rebuild());
    }

    @GetMapping("/search/status")
    public ApiResponse<SearchIndexStatusResponse> searchStatus() {
        return ApiResponse.ofSuccess(orderSearchService.status());
    }

    @PostMapping("/{id}/cancel")
    public ApiResponse<OrderResponse> cancel(@PathVariable("id") Long id, @Valid @RequestBody CancelOrderRequest request) {
        return ApiResponse.ofSuccess(orderService.cancel(CustomerIdentity.currentCustomerId(), id, request.reason()));
    }
}
