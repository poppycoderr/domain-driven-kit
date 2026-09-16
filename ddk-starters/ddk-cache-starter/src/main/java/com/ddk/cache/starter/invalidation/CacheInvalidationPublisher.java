package com.ddk.cache.starter.invalidation;

/**
 * 通知其他实例清理它们的 L1。
 * <p>
 * 只有 L1 需要广播：L2 是共享的，一个实例写了所有实例都看得到。
 *
 * @author Elijah Du
 */
public interface CacheInvalidationPublisher {

    CacheInvalidationPublisher NOOP = new CacheInvalidationPublisher() {
        @Override
        public void publishEvict(String cacheName, String key) {
        }

        @Override
        public void publishClear(String cacheName) {
        }
    };

    void publishEvict(String cacheName, String key);

    void publishClear(String cacheName);
}
