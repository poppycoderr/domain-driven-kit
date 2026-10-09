package com.example.mall.order.application.service;

import com.ddk.core.exception.BusinessException;
import com.ddk.core.page.PageResponse;
import com.ddk.projection.starter.Projections;
import com.example.mall.order.application.projection.OrderSearchProjection;
import com.example.mall.order.application.query.OrderSearchQuery;
import com.example.mall.order.application.response.OrderSummaryResponse;
import com.example.mall.order.application.response.SearchIndexStatusResponse;
import com.example.mall.order.domain.acl.OrderSearchIndex;
import com.example.mall.order.domain.error.OrderError;
import com.example.mall.order.domain.model.OrderStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 订单搜索：查的是读模型，不是订单表。读模型在订单提交之后才刷新，所以刚下的单可能要过一小会儿才搜得到。
 */
@Service
@RequiredArgsConstructor
public class OrderSearchService {

    private final OrderSearchIndex searchIndex;

    private final Projections projections;

    public PageResponse<OrderSummaryResponse> search(Long customerId, OrderSearchQuery query) {
        return searchIndex.search(customerId, query.getKeyword(), status(query.getStatus()), query)
                .map(summaries -> summaries.stream().map(OrderSummaryResponse::from).toList());
    }

    /**
     * 重建读模型：把每个订单都标记为需要刷新，由后台逐个处理。方法返回时刷新可能还没做完。
     */
    public SearchIndexStatusResponse rebuild() {
        long marked = projections.rebuild(OrderSearchProjection.NAME);
        return new SearchIndexStatusResponse(marked, projections.pending(OrderSearchProjection.NAME));
    }

    public SearchIndexStatusResponse status() {
        return new SearchIndexStatusResponse(0, projections.pending(OrderSearchProjection.NAME));
    }

    private static OrderStatus status(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return OrderStatus.valueOf(value);
        } catch (IllegalArgumentException e) {
            throw new BusinessException(OrderError.UNKNOWN_STATUS, value);
        }
    }
}
