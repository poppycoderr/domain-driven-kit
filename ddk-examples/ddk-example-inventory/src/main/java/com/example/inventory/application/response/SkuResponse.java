package com.example.inventory.application.response;

import com.example.inventory.domain.model.Sku;

/**
 * 商品的当前库存。也是缓存里存的值，所以要能被 Redis 的 JSON 序列化读写。
 */
public record SkuResponse(
        Long id,
        String name,
        int available
) {

    public static SkuResponse from(Sku sku) {
        return new SkuResponse(sku.skuId().value(), sku.name(), sku.available());
    }
}
