package com.example.mall.order.application.projection;

import com.ddk.projection.starter.Projection;
import com.example.mall.order.domain.acl.OrderRepository;
import com.example.mall.order.domain.acl.OrderSearchIndex;
import com.example.mall.order.domain.model.OrderId;
import com.example.mall.order.domain.model.OrderSummary;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.function.Consumer;

/**
 * 订单搜索读模型的投影：订单变了之后，用订单现在的状态重新生成它在读模型里的那一条。
 * <p>
 * 这里不关心是哪个事件引起的变化。下单、库存确认、支付、取消都只是「这个订单变了」，做的事情完全一样，
 * 所以事件乱序、重复都不会让读模型出错。重建时走的也是这个方法。
 */
@Component
@RequiredArgsConstructor
public class OrderSearchProjection implements Projection {

    public static final String NAME = "order-search";

    private static final int REBUILD_PAGE_SIZE = 500;

    private final OrderRepository orderRepository;

    private final OrderSearchIndex searchIndex;

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public void refresh(String id) {
        OrderId orderId = OrderId.of(Long.valueOf(id));
        orderRepository.find(orderId).ifPresentOrElse(order -> searchIndex.save(OrderSummary.of(order)), () -> searchIndex.remove(orderId));
    }

    /**
     * 按订单号一页一页往后读，不把全部订单号一次读进内存。
     */
    @Override
    public void forEachId(Consumer<String> ids) {
        OrderId last = null;
        List<OrderId> page;
        do {
            page = orderRepository.findIdsAfter(last, REBUILD_PAGE_SIZE);
            for (OrderId id : page) {
                ids.accept(String.valueOf(id.value()));
                last = id;
            }
        } while (page.size() == REBUILD_PAGE_SIZE);
    }
}
