package com.ddk.projection.starter;

import org.jspecify.annotations.Nullable;

import java.time.Instant;

/**
 * 一条刷新失败、正在等待重试的标记，用于定位一直失败的数据。
 *
 * @param tenant      所属租户，没有租户时为空字符串
 * @param id          数据标识
 * @param attempts    已经失败的次数
 * @param lastError   最近一次失败的异常，截断到 500 个字符
 * @param nextAttempt 下一次重试的时间
 */
public record FailedRefresh(
        String tenant,

        String id,

        int attempts,

        @Nullable String lastError,

        Instant nextAttempt
) {
}
