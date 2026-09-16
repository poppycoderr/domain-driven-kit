package com.ddk.cache.starter.invalidation;

/**
 * 失效广播消息。
 *
 * @param origin    发送方实例 ID，接收方据此跳过自己发出的消息
 * @param cacheName 缓存名
 * @param key       要清理的 key；为 null 表示清空整个缓存
 * @author Elijah Du
 */
public record CacheInvalidationMessage(String origin, String cacheName, String key) {

    /**
     * 刻意不叫 {@code isClear()}：Jackson 会把 is/get 方法当属性序列化，
     * 多出的 {@code clear} 字段在接收端反序列化时报未知属性，消息被整条丢弃。
     */
    public boolean clearsWholeCache() {
        return key == null;
    }
}
