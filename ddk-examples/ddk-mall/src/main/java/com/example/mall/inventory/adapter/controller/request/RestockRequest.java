package com.example.mall.inventory.adapter.controller.request;

import jakarta.validation.constraints.Min;

/**
 * 补货请求。
 */
public record RestockRequest(
        @Min(1)
        int quantity
) {
}
