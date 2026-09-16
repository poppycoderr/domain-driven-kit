package com.ddk.cache.starter.support;

import java.time.Duration;

/**
 * 单个缓存合并默认值与个性化配置之后的最终设置。
 *
 * @param name             缓存名
 * @param ttl              L2 过期时间，{@link Duration#ZERO} 表示不过期
 * @param localEnabled     是否启用 L1
 * @param localTtl         L1 过期时间
 * @param localMaximumSize L1 最大条目数
 * @author Elijah Du
 */
public record CacheSettings(String name, Duration ttl, boolean localEnabled, Duration localTtl, long localMaximumSize) {
}
