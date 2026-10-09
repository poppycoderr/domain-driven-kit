package com.example.mall.order.infrastructure.acl.impl;

import com.example.mall.order.domain.acl.ProductCatalog;
import com.example.mall.order.domain.model.Money;
import com.example.mall.order.infrastructure.orm.mapper.ProductMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * 商品目录的实现：读商品表，结果放进缓存。
 * <p>
 * 商品的名称和单价很少变，下单时每一行都要查一次，所以适合缓存，并且打开了进程内的一级缓存。
 * 不存在的 SKU 也会被缓存一小段时间（DDK 默认缓存空值），反复用不存在的 SKU 下单不会每次都打到数据库。
 */
@Component
@RequiredArgsConstructor
public class ProductCatalogImpl implements ProductCatalog {

    private final ProductMapper productMapper;

    @Override
    @Cacheable(cacheNames = "product", key = "#skuId")
    public Optional<Product> find(String skuId) {
        return Optional.ofNullable(productMapper.selectById(skuId))
                .map(po -> new Product(po.getSkuId(), po.getName(), new Money(po.getUnitPrice())));
    }
}
