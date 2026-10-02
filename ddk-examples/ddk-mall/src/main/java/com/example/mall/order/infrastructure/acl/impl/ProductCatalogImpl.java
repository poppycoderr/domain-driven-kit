package com.example.mall.order.infrastructure.acl.impl;

import com.example.mall.order.domain.acl.ProductCatalog;
import com.example.mall.order.domain.model.Money;
import com.example.mall.order.infrastructure.orm.mapper.ProductMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * 商品目录的实现：直接读商品表。
 */
@Component
@RequiredArgsConstructor
public class ProductCatalogImpl implements ProductCatalog {

    private final ProductMapper productMapper;

    @Override
    public Optional<Product> find(String skuId) {
        return Optional.ofNullable(productMapper.selectById(skuId))
                .map(po -> new Product(po.getSkuId(), po.getName(), new Money(po.getUnitPrice())));
    }
}
