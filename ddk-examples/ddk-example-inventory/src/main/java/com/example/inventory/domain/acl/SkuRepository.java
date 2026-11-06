package com.example.inventory.domain.acl;

import com.example.inventory.domain.model.Sku;
import com.example.inventory.domain.model.SkuId;

import java.util.Optional;

/**
 * 商品的存取端口。
 */
public interface SkuRepository {

    Optional<Sku> find(SkuId id);

    void update(Sku sku);
}
