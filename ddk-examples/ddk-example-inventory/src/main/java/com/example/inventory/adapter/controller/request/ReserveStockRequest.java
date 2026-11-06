package com.example.inventory.adapter.controller.request;

import com.example.inventory.application.command.ReserveStockCommand;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

/**
 * 预留库存的请求体。
 */
public record ReserveStockRequest(
        @NotBlank String requestId,
        @Positive int quantity
) {

    public ReserveStockCommand toCommand(Long skuId) {
        return new ReserveStockCommand(requestId, skuId, quantity);
    }
}
