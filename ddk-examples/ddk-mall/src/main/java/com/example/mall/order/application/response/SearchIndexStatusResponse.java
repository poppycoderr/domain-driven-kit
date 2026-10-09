package com.example.mall.order.application.response;

/**
 * 订单搜索读模型的状态。
 *
 * @param marked  这次重建标记了多少个订单；只查询状态时为 0
 * @param pending 还有多少个订单在等待刷新
 */
public record SearchIndexStatusResponse(
        long marked,

        long pending
) {
}
