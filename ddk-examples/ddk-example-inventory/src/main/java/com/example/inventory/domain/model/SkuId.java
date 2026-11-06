package com.example.inventory.domain.model;

import com.ddk.core.domain.Identifier;

/**
 * 商品标识。
 */
public final class SkuId extends Identifier<Long> {

    private SkuId(Long value) {
        super(value);
        if (value <= 0) {
            throw new IllegalArgumentException("SkuId must be positive, got " + value);
        }
    }

    public static SkuId of(Long value) {
        return new SkuId(value);
    }
}
