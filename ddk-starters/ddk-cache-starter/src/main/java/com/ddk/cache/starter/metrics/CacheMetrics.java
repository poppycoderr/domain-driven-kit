package com.ddk.cache.starter.metrics;

/**
 * 缓存访问计数。
 * <p>
 * 缓存是最容易「以为生效了其实没生效」的组件，命中率必须能直接看到。
 * 标签只放缓存名这种有限枚举，绝不放 key——那是无界基数。
 *
 * @author Elijah Du
 */
public interface CacheMetrics {

    CacheMetrics NOOP = new CacheMetrics() {
        @Override
        public void access(String cacheName, AccessResult result) {
        }

        @Override
        public void remoteError(String cacheName, String operation) {
        }
    };

    void access(String cacheName, AccessResult result);

    void remoteError(String cacheName, String operation);

    enum AccessResult {
        L1_HIT, L2_HIT, MISS;

        public String tag() {
            return name().toLowerCase();
        }
    }
}
