package com.example.inventory.adapter.controller;

import com.ddk.core.response.ApiResponse;
import com.example.inventory.adapter.controller.request.ReserveStockRequest;
import com.example.inventory.application.response.LowStockResponse;
import com.example.inventory.application.response.SkuResponse;
import com.example.inventory.application.service.InventoryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 库存接口。
 */
@RestController
@RequiredArgsConstructor
public class InventoryController {

    private final InventoryService inventory;

    @GetMapping("/skus/{id}")
    public ApiResponse<SkuResponse> find(@PathVariable("id") Long id) {
        return ApiResponse.ofSuccess(inventory.find(id));
    }

    @GetMapping("/skus/{id}/availability")
    public ApiResponse<SkuResponse> availability(@PathVariable("id") Long id) {
        return ApiResponse.ofSuccess(inventory.checkAvailability(id));
    }

    @PostMapping("/skus/{id}/reservations")
    public ApiResponse<SkuResponse> reserve(@PathVariable("id") Long id, @Valid @RequestBody ReserveStockRequest request) {
        return ApiResponse.ofSuccess(inventory.reserve(request.toCommand(id)));
    }

    @GetMapping("/skus/{id}/reservations")
    public ApiResponse<List<String>> reservations(@PathVariable("id") Long id) {
        return ApiResponse.ofSuccess(inventory.reservationsOf(id));
    }

    @GetMapping("/low-stock")
    public ApiResponse<List<LowStockResponse>> lowStock() {
        return ApiResponse.ofSuccess(inventory.lowStock());
    }
}
