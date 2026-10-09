package com.example.mall.order.infrastructure.acl.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ddk.core.page.PageQuery;
import com.ddk.core.page.PageResponse;
import com.ddk.mybatis.page.MybatisPlusPageAdapter;
import com.example.mall.order.domain.acl.OrderSearchIndex;
import com.example.mall.order.domain.model.Money;
import com.example.mall.order.domain.model.OrderId;
import com.example.mall.order.domain.model.OrderStatus;
import com.example.mall.order.domain.model.OrderSummary;
import com.example.mall.order.infrastructure.orm.mapper.OrderSearchMapper;
import com.example.mall.order.infrastructure.orm.po.OrderSearchPO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * 订单搜索读模型的实现：关系库里的一张宽表，商品名称用 LIKE 匹配。数据量上去之后换成搜索引擎，接口不变。
 * <p>
 * 写入是「有则更新、无则插入」。两个线程同时为同一个订单插入时后一个会撞上主键而失败，这次刷新随之失败，
 * 由投影 starter 稍后重试，重试时走的是更新。
 */
@Component
@RequiredArgsConstructor
public class OrderSearchIndexImpl implements OrderSearchIndex {

    private final OrderSearchMapper mapper;

    @Override
    public void save(OrderSummary summary) {
        OrderSearchPO po = new OrderSearchPO();
        po.setOrderId(summary.orderId());
        po.setCustomerId(summary.customerId());
        po.setStatus(summary.status().name());
        po.setTotalAmount(summary.totalAmount().amount());
        po.setItemCount(summary.itemCount());
        po.setProductNames(summary.productNames());
        po.setExpiresAt(summary.expiresAt());
        po.setRefreshedAt(Instant.now());
        if (mapper.updateById(po) == 0) {
            mapper.insert(po);
        }
    }

    @Override
    public void remove(OrderId orderId) {
        mapper.deleteById(orderId.value());
    }

    @Override
    public PageResponse<OrderSummary> search(Long customerId, String keyword, OrderStatus status, PageQuery page) {
        boolean hasKeyword = keyword != null && !keyword.isBlank();
        Page<OrderSearchPO> result = mapper.selectPage(MybatisPlusPageAdapter.toPage(page), Wrappers.lambdaQuery(OrderSearchPO.class)
                .eq(OrderSearchPO::getCustomerId, customerId)
                .eq(status != null, OrderSearchPO::getStatus, status == null ? null : status.name())
                .like(hasKeyword, OrderSearchPO::getProductNames, hasKeyword ? keyword.trim() : null)
                .orderByDesc(OrderSearchPO::getOrderId));
        return MybatisPlusPageAdapter.toPageResponse(result, pos -> pos.stream().map(OrderSearchIndexImpl::toSummary).toList());
    }

    private static OrderSummary toSummary(OrderSearchPO po) {
        return new OrderSummary(po.getOrderId(), po.getCustomerId(), OrderStatus.valueOf(po.getStatus()), new Money(po.getTotalAmount()),
                po.getItemCount(), po.getProductNames(), po.getExpiresAt());
    }
}
