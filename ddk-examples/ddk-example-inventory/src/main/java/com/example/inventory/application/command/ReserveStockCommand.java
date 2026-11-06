package com.example.inventory.application.command;

/**
 * 预留库存。{@code requestId} 由客户端生成，重试时不变。
 */
public record ReserveStockCommand(
        String requestId,
        Long skuId,
        int quantity
) {
}
