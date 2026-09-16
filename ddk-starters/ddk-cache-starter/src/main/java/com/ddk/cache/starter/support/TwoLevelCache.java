package com.ddk.cache.starter.support;

import com.ddk.cache.starter.invalidation.CacheInvalidationPublisher;
import com.ddk.cache.starter.metrics.CacheMetrics;
import com.ddk.cache.starter.metrics.CacheMetrics.AccessResult;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Expiry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.cache.support.AbstractValueAdaptingCache;
import org.springframework.cache.support.NullValue;
import org.springframework.core.convert.ConversionService;
import org.springframework.lang.Nullable;

import java.time.Duration;
import java.util.concurrent.Callable;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 两级缓存：L1 是进程内的 Caffeine，L2 是共享的 Redis。
 *
 * <h2>为什么在 Cache 层而不是 CacheManager 层实现</h2>
 * {@code CompositeCacheManager} 按名字路由：第一个能答出这个缓存名的 manager 就拿走整个请求，
 * 而 Caffeine 对任何名字都答得出，Redis 永远收不到请求。
 * 分层查找只能发生在单个 {@link Cache} 实例内部。
 *
 * <h2>读写顺序</h2>
 * <ul>
 *     <li>读：L1 → L2 → 回填 L1</li>
 *     <li>写：先 L2 后 L1。反过来的话 L2 写失败会留下只有本实例看得到的值</li>
 *     <li>写和失效之后广播，让其他实例清掉各自 L1 里的旧值</li>
 * </ul>
 *
 * <h2>Redis 故障</h2>
 * L2 的任何异常都被当作未命中处理并记录指标，不向业务抛出：缓存是优化手段，不该成为可用性单点。
 * 启用了 L1 的缓存在故障期间继续由 L1 提供服务。
 *
 * <h2>key</h2>
 * L1 和 L2 统一使用转换后的字符串 key。失效广播只能传字符串，
 * 如果 L1 用原始对象做 key，收到 {@code "42"} 时清不掉以 {@code 42L} 存进去的条目。
 *
 * @author Elijah Du
 */
@Slf4j
public class TwoLevelCache extends AbstractValueAdaptingCache {

    private final CacheSettings settings;

    @Nullable
    private final com.github.benmanes.caffeine.cache.Cache<String, Object> local;

    @Nullable
    private final Cache remote;

    private final ConversionService conversionService;
    private final CacheInvalidationPublisher publisher;
    private final CacheMetrics metrics;

    /**
     * 没有 L1 时用于 {@code @Cacheable(sync = true)} 的进程内互斥，与 Spring 的 RedisCache 行为一致。
     */
    private final ReentrantLock loadLock = new ReentrantLock();

    public TwoLevelCache(CacheSettings settings,
                         boolean allowNullValues,
                         Duration nullValueTtl,
                         @Nullable Cache remote,
                         ConversionService conversionService,
                         CacheInvalidationPublisher publisher,
                         CacheMetrics metrics) {
        super(allowNullValues);
        this.settings = settings;
        this.remote = remote;
        this.conversionService = conversionService;
        this.publisher = publisher;
        this.metrics = metrics;
        // 没有 L2 时强制启用 L1，否则缓存什么也不做
        this.local = settings.localEnabled() || remote == null ? buildLocal(settings, nullValueTtl) : null;
    }

    @Override
    public String getName() {
        return settings.name();
    }

    @Override
    public Object getNativeCache() {
        return this;
    }

    public boolean hasLocal() {
        return local != null;
    }

    public boolean hasRemote() {
        return remote != null;
    }

    @Override
    @Nullable
    protected Object lookup(Object key) {
        String cacheKey = toCacheKey(key);
        if (local != null) {
            Object value = local.getIfPresent(cacheKey);
            if (value != null) {
                metrics.access(getName(), AccessResult.L1_HIT);
                return value;
            }
        }
        Object value = remoteLookup(cacheKey);
        if (value != null) {
            metrics.access(getName(), AccessResult.L2_HIT);
            if (local != null) {
                local.put(cacheKey, value);
            }
            return value;
        }
        metrics.access(getName(), AccessResult.MISS);
        return null;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T get(Object key, Callable<T> valueLoader) {
        String cacheKey = toCacheKey(key);
        Object stored = lookup(cacheKey);
        if (stored != null) {
            return (T) fromStoreValue(stored);
        }
        if (local != null) {
            // Caffeine 对同一个 key 的计算是串行的，其余线程等待并复用结果：进程内单飞
            return (T) fromStoreValue(local.get(cacheKey, k -> load(k, key, valueLoader)));
        }
        loadLock.lock();
        try {
            return (T) fromStoreValue(load(cacheKey, key, valueLoader));
        } finally {
            loadLock.unlock();
        }
    }

    private Object load(String cacheKey, Object originalKey, Callable<?> valueLoader) {
        // 等待期间别的线程或别的实例可能已经写入 L2
        Object existing = remoteLookup(cacheKey);
        if (existing != null) {
            return existing;
        }
        Object value;
        try {
            value = valueLoader.call();
        } catch (Exception e) {
            throw new ValueRetrievalException(originalKey, valueLoader, e);
        }
        Object storeValue = toStoreValue(value);
        remotePut(cacheKey, value);
        return storeValue;
    }

    @Override
    public void put(Object key, @Nullable Object value) {
        String cacheKey = toCacheKey(key);
        Object storeValue = toStoreValue(value);
        remotePut(cacheKey, value);
        if (local != null) {
            local.put(cacheKey, storeValue);
            publisher.publishEvict(getName(), cacheKey);
        }
    }

    @Override
    public void evict(Object key) {
        String cacheKey = toCacheKey(key);
        if (remote != null) {
            try {
                remote.evict(cacheKey);
            } catch (RuntimeException e) {
                remoteFailure("evict", e);
            }
        }
        if (local != null) {
            local.invalidate(cacheKey);
            publisher.publishEvict(getName(), cacheKey);
        }
    }

    @Override
    public void clear() {
        if (remote != null) {
            try {
                remote.clear();
            } catch (RuntimeException e) {
                remoteFailure("clear", e);
            }
        }
        if (local != null) {
            local.invalidateAll();
            publisher.publishClear(getName());
        }
    }

    /**
     * 收到其他实例的失效广播时调用：只清 L1，不再广播。
     */
    public void evictLocal(String cacheKey) {
        if (local != null) {
            local.invalidate(cacheKey);
        }
    }

    public void clearLocal() {
        if (local != null) {
            local.invalidateAll();
        }
    }

    /**
     * 只查 L1，不触发 L2 与指标。供测试和诊断使用。
     */
    @Nullable
    public Object peekLocal(Object key) {
        return local == null ? null : local.getIfPresent(toCacheKey(key));
    }

    String toCacheKey(Object key) {
        if (key instanceof String s) {
            return s;
        }
        if (conversionService.canConvert(key.getClass(), String.class)) {
            String converted = conversionService.convert(key, String.class);
            if (converted != null) {
                return converted;
            }
        }
        return key.toString();
    }

    @Nullable
    private Object remoteLookup(String cacheKey) {
        if (remote == null) {
            return null;
        }
        try {
            ValueWrapper wrapper = remote.get(cacheKey);
            if (wrapper == null) {
                return null;
            }
            Object value = wrapper.get();
            if (value == null) {
                return isAllowNullValues() ? NullValue.INSTANCE : null;
            }
            return value;
        } catch (RuntimeException e) {
            remoteFailure("get", e);
            return null;
        }
    }

    private void remotePut(String cacheKey, @Nullable Object value) {
        if (remote == null) {
            return;
        }
        try {
            remote.put(cacheKey, value);
        } catch (RuntimeException e) {
            remoteFailure("put", e);
        }
    }

    private void remoteFailure(String operation, RuntimeException e) {
        metrics.remoteError(getName(), operation);
        log.warn("L2 cache [{}] {} failed, falling back: {}", getName(), operation, e.toString());
    }

    private static com.github.benmanes.caffeine.cache.Cache<String, Object> buildLocal(CacheSettings settings,
                                                                                       Duration nullValueTtl) {
        long ttl = settings.localTtl().toNanos();
        long nullTtl = Math.min(ttl, nullValueTtl.toNanos());
        return Caffeine.newBuilder()
                .maximumSize(settings.localMaximumSize())
                .expireAfter(new Expiry<String, Object>() {
                    @Override
                    public long expireAfterCreate(String key, Object value, long currentTime) {
                        return value instanceof NullValue ? nullTtl : ttl;
                    }

                    @Override
                    public long expireAfterUpdate(String key, Object value, long currentTime, long currentDuration) {
                        return expireAfterCreate(key, value, currentTime);
                    }

                    @Override
                    public long expireAfterRead(String key, Object value, long currentTime, long currentDuration) {
                        return currentDuration;
                    }
                })
                .build();
    }
}
