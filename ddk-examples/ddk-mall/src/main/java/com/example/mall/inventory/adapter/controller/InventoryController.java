package com.example.mall.inventory.adapter.controller;

import com.ddk.core.response.ApiResponse;
import com.example.mall.inventory.adapter.controller.request.ReserveStockRequest;
import com.example.mall.inventory.adapter.controller.request.RestockRequest;
import com.example.mall.inventory.application.response.ReservationResponse;
import com.example.mall.inventory.application.response.StockResponse;
import com.example.mall.inventory.application.service.InventoryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 库存接口。预占、释放、扣减在第 3 步之后由订单事件驱动，这里的接口用于运营操作和手工验证。
 */
@RestController
@RequestMapping("/inventory")
@RequiredArgsConstructor
public class InventoryController {

    private final InventoryService inventoryService;

    @GetMapping("/{skuId}")
    public ApiResponse<StockResponse> get(@PathVariable("skuId") String skuId) {
        return ApiResponse.ofSuccess(inventoryService.get(skuId));
    }

    @PostMapping("/{skuId}/restock")
    public ApiResponse<StockResponse> restock(@PathVariable("skuId") String skuId, @Valid @RequestBody RestockRequest request) {
        return ApiResponse.ofSuccess(inventoryService.restock(skuId, request.quantity()));
    }

    @PostMapping("/reservations")
    public ApiResponse<List<ReservationResponse>> reserve(@Valid @RequestBody ReserveStockRequest request) {
        return ApiResponse.ofSuccess(inventoryService.reserve(request.toCommand()));
    }

    @GetMapping("/reservations/{orderId}")
    public ApiResponse<List<ReservationResponse>> reservations(@PathVariable("orderId") Long orderId) {
        return ApiResponse.ofSuccess(inventoryService.reservationsOf(orderId));
    }

    @PostMapping("/reservations/{orderId}/release")
    public ApiResponse<List<ReservationResponse>> release(@PathVariable("orderId") Long orderId) {
        return ApiResponse.ofSuccess(inventoryService.release(orderId));
    }

    @PostMapping("/reservations/{orderId}/confirm")
    public ApiResponse<List<ReservationResponse>> confirm(@PathVariable("orderId") Long orderId) {
        return ApiResponse.ofSuccess(inventoryService.confirm(orderId));
    }
}
