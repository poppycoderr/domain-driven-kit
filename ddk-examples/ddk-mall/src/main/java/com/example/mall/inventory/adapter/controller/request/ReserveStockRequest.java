package com.example.mall.inventory.adapter.controller.request;

import com.example.mall.inventory.application.command.ReserveStockCommand;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.util.List;

/**
 * 预占库存请求。
 */
public record ReserveStockRequest(
        @NotNull
        @Positive
        Long orderId,

        @NotEmpty
        @Valid
        List<Line> lines
) {

    public ReserveStockCommand toCommand() {
        return new ReserveStockCommand(orderId, lines.stream().map(line -> new ReserveStockCommand.Line(line.skuId(), line.quantity())).toList());
    }

    public record Line(
            @NotBlank
            String skuId,

            @Min(1)
            int quantity
    ) {
    }
}
