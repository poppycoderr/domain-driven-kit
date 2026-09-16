package com.ddk.cache.starter.support;

import org.junit.jupiter.api.Test;
import org.springframework.cache.Cache;
import org.springframework.cache.concurrent.ConcurrentMapCache;
import org.springframework.cache.support.NullValue;
import org.springframework.core.convert.support.DefaultConversionService;
import org.springframework.data.redis.RedisConnectionFailureException;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class TwoLevelCacheTest {

    private final ConcurrentMapCache remote = new ConcurrentMapCache("users");
    private final Recorders.Metrics metrics = new Recorders.Metrics();
    private final Recorders.Publisher publisher = new Recorders.Publisher();

    private TwoLevelCache cache(boolean localEnabled, Cache remoteCache) {
        CacheSettings settings = new CacheSettings("users", Duration.ofMinutes(30), localEnabled,
                Duration.ofSeconds(30), 100);
        return new TwoLevelCache(settings, true, Duration.ofMinutes(1), remoteCache,
                new DefaultConversionService(), publisher, metrics);
    }

    @Test
    void remoteHitIsBackfilledIntoLocal() {
        TwoLevelCache cache = cache(true, remote);
        remote.put("1", "alice");

        assertThat(cache.get("1").get()).isEqualTo("alice");
        assertThat(cache.peekLocal("1")).isEqualTo("alice");
        assertThat(cache.get("1").get()).isEqualTo("alice");

        assertThat(metrics.count("l2_hit")).isEqualTo(1);
        assertThat(metrics.count("l1_hit")).isEqualTo(1);
    }

    @Test
    void putWritesBothLevelsAndBroadcasts() {
        TwoLevelCache cache = cache(true, remote);

        cache.put(1L, "alice");

        assertThat(remote.get("1").get()).isEqualTo("alice");
        assertThat(cache.peekLocal(1L)).isEqualTo("alice");
        assertThat(publisher.messages).containsExactly("evict users 1");
    }

    @Test
    void evictAndClearRemoveBothLevelsAndBroadcast() {
        TwoLevelCache cache = cache(true, remote);
        cache.put("1", "alice");
        cache.put("2", "bob");

        cache.evict("1");
        assertThat(remote.get("1")).isNull();
        assertThat(cache.peekLocal("1")).isNull();

        cache.clear();
        assertThat(remote.get("2")).isNull();
        assertThat(cache.peekLocal("2")).isNull();
        assertThat(publisher.messages).endsWith("evict users 1", "clear users");
    }

    @Test
    void remoteOnlyCacheNeitherKeepsLocalCopiesNorBroadcasts() {
        TwoLevelCache cache = cache(false, remote);

        cache.put("1", "alice");

        assertThat(cache.hasLocal()).isFalse();
        assertThat(cache.get("1").get()).isEqualTo("alice");
        assertThat(publisher.messages).isEmpty();
    }

    @Test
    void cacheWithoutRemoteAlwaysUsesLocal() {
        TwoLevelCache cache = cache(false, null);

        cache.put("1", "alice");

        assertThat(cache.hasLocal()).isTrue();
        assertThat(cache.get("1").get()).isEqualTo("alice");
    }

    @Test
    void broadcastKeyEvictsEntryStoredUnderNonStringKey() {
        TwoLevelCache cache = cache(true, remote);
        cache.put(42L, "alice");

        cache.evictLocal("42");

        assertThat(cache.peekLocal(42L)).isNull();
    }

    @Test
    void valueLoaderRunsOnceUnderConcurrency() throws Exception {
        TwoLevelCache cache = cache(true, remote);
        AtomicInteger calls = new AtomicInteger();
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(8);
        try {
            List<Future<String>> results = new ArrayList<>();
            for (int i = 0; i < 8; i++) {
                results.add(pool.submit(() -> {
                    start.await();
                    return cache.get("1", () -> {
                        calls.incrementAndGet();
                        Thread.sleep(50);
                        return "alice";
                    });
                }));
            }
            start.countDown();
            for (Future<String> result : results) {
                assertThat(result.get(5, TimeUnit.SECONDS)).isEqualTo("alice");
            }
        } finally {
            pool.shutdownNow();
        }

        assertThat(calls).hasValue(1);
        assertThat(remote.get("1").get()).isEqualTo("alice");
    }

    @Test
    void valueLoaderIsSkippedWhenAnotherInstanceAlreadyWroteRemote() {
        TwoLevelCache cache = cache(false, remote);
        remote.put("1", "from-other-instance");

        String value = cache.get("1", () -> "loaded");

        assertThat(value).isEqualTo("from-other-instance");
    }

    @Test
    void loaderFailureIsWrappedAndNothingIsCached() {
        TwoLevelCache cache = cache(true, remote);

        assertThatThrownBy(() -> cache.get("1", () -> {
            throw new IllegalStateException("db down");
        })).isInstanceOf(Cache.ValueRetrievalException.class).hasRootCauseMessage("db down");

        assertThat(remote.get("1")).isNull();
        assertThat(cache.peekLocal("1")).isNull();
    }

    @Test
    void nullResultIsCachedToPreventPenetration() {
        TwoLevelCache cache = cache(true, remote);
        AtomicInteger calls = new AtomicInteger();

        assertThat(cache.<String>get("missing", () -> { calls.incrementAndGet(); return null; })).isNull();
        assertThat(cache.<String>get("missing", () -> { calls.incrementAndGet(); return null; })).isNull();

        assertThat(calls).hasValue(1);
        assertThat(cache.peekLocal("missing")).isEqualTo(NullValue.INSTANCE);
    }

    @Test
    void remoteFailureDegradesToLocalWithoutThrowing() {
        Cache broken = mock(Cache.class, invocation -> {
            throw new RedisConnectionFailureException("redis down");
        });
        TwoLevelCache cache = cache(true, broken);

        assertThatNoException().isThrownBy(() -> cache.put("1", "alice"));
        assertThat(cache.get("1").get()).isEqualTo("alice");
        assertThat(cache.get("2", () -> "bob")).isEqualTo("bob");
        assertThatNoException().isThrownBy(() -> cache.evict("1"));

        assertThat(metrics.count("error:put")).isEqualTo(2);
        assertThat(metrics.count("error:get")).isPositive();
    }

    @Test
    void remoteOnlyCacheStillServesRequestsWhenRedisIsDown() {
        Cache broken = mock(Cache.class, invocation -> {
            throw new RedisConnectionFailureException("redis down");
        });
        TwoLevelCache cache = cache(false, broken);
        AtomicInteger calls = new AtomicInteger();

        assertThat(cache.<String>get("1", () -> { calls.incrementAndGet(); return "alice"; })).isEqualTo("alice");
        assertThat(cache.<String>get("1", () -> { calls.incrementAndGet(); return "alice"; })).isEqualTo("alice");

        // 没有 L1 又没有 L2，只能每次回源——但业务不受影响
        assertThat(calls).hasValue(2);
    }
}
