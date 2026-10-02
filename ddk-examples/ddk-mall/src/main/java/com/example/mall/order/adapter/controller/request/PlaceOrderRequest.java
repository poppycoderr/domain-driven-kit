package com.example.mall.order.adapter.controller.request;

import com.example.mall.order.application.command.PlaceOrderCommand;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

/**
 * 下单请求。这里只拦明显不合法的输入，下单规则由领域模型保证。
 */
public record PlaceOrderRequest(
        @NotEmpty
        @Valid
        List<Line> lines
) {

    public PlaceOrderCommand toCommand(Long customerId) {
        return new PlaceOrderCommand(customerId, lines.stream().map(line -> new PlaceOrderCommand.Line(line.skuId(), line.quantity())).toList());
    }

    public record Line(
            @NotBlank
            String skuId,

            @Min(1)
            int quantity
    ) {
    }
}
