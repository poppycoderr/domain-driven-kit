package com.ddk.cache.starter.invalidation;

import org.jspecify.annotations.Nullable;

/**
 * 失效广播消息。
 *
 * @param origin    发送方实例 ID，接收方据此跳过自己发出的消息
 * @param cacheName 缓存名
 * @param key       要清理的 key；为 null 表示清空整个缓存
 * @author Elijah Du
 */
public record CacheInvalidationMessage(String origin, String cacheName, @Nullable String key) {
}
