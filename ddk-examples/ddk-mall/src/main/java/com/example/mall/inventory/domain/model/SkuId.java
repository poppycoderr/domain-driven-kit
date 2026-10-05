package com.example.mall.inventory.domain.model;

import com.ddk.core.domain.Identifier;

/**
 * 库存上下文里的 SKU 标识。
 */
public final class SkuId extends Identifier<String> {

    private SkuId(String value) {
        super(value);
        if (value.isBlank()) {
            throw new IllegalArgumentException("SkuId must not be blank");
        }
    }

    public static SkuId of(String value) {
        return new SkuId(value);
    }
}
