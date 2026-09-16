package com.ddk.cache.starter.support;

import com.ddk.cache.starter.invalidation.CacheInvalidationPublisher;
import com.ddk.cache.starter.metrics.CacheMetrics;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

final class Recorders {

    private Recorders() {
    }

    static final class Metrics implements CacheMetrics {

        private final Map<String, AtomicInteger> counts = new ConcurrentHashMap<>();

        @Override
        public void access(String cacheName, AccessResult result) {
            counts.computeIfAbsent(result.tag(), k -> new AtomicInteger()).incrementAndGet();
        }

        @Override
        public void remoteError(String cacheName, String operation) {
            counts.computeIfAbsent("error:" + operation, k -> new AtomicInteger()).incrementAndGet();
        }

        int count(String name) {
            AtomicInteger count = counts.get(name);
            return count == null ? 0 : count.get();
        }
    }

    static final class Publisher implements CacheInvalidationPublisher {

        final List<String> messages = new CopyOnWriteArrayList<>();

        @Override
        public void publishEvict(String cacheName, String key) {
            messages.add("evict " + cacheName + " " + key);
        }

        @Override
        public void publishClear(String cacheName) {
            messages.add("clear " + cacheName);
        }
    }
}
