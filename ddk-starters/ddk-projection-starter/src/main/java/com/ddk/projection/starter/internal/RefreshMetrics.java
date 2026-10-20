package com.ddk.projection.starter.internal;

import java.time.Duration;
import java.util.Collection;

/**
 * 刷新过程的指标出口。没有 Micrometer 时用 {@link #NONE}。
 */
public interface RefreshMetrics {

    RefreshMetrics NONE = new RefreshMetrics() {

        @Override
        public void bind(Collection<String> projections, PendingRefreshes pending) {
        }

        @Override
        public void refreshed(String projection, Outcome outcome, Duration took) {
        }
    };

    /**
     * 注册按读模型统计待刷新表的指标。worker 启动时调用一次。
     */
    void bind(Collection<String> projections, PendingRefreshes pending);

    void refreshed(String projection, Outcome outcome, Duration took);

    enum Outcome {

        /**
         * 刷新完成，标记已删除。
         */
        SUCCESS,

        /**
         * 刷新完成，但期间又有变化或被别人接手，已重新标记。
         */
        REPEATED,

        /**
         * {@code refresh} 抛出异常，推迟重试。
         */
        FAILED
    }
}
