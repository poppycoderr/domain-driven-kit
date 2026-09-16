# DDK Cache Starter

A two-level cache behind the standard Spring Cache annotations: Caffeine (L1, per instance) in front of Redis (L2, shared).

```text
@Cacheable read
  L1 hit ──────────────────────────────► return
  L1 miss → L2 hit → backfill L1 ──────► return
  L1 miss → L2 miss → invoke method → write L2 → write L1 → return

@CachePut / @CacheEvict
  write or evict L2 → L1 → broadcast "evict key" to other instances' L1
```

## Why a Cache, not a CacheManager

`CompositeCacheManager` routes by cache name: the first manager that knows a name serves every request, and Caffeine knows every name. Redis would never be reached. Layered lookup has to happen inside a single `Cache`, which is what `TwoLevelCache` does.

## Usage

```xml
<dependency>
    <groupId>com.ddk</groupId>
    <artifactId>ddk-cache-starter</artifactId>
</dependency>
```

Then use `@Cacheable` / `@CachePut` / `@CacheEvict` as usual. `@EnableCaching` is already applied.

```yaml
ddk:
  cache:
    default-ttl: 30m          # L2 TTL
    key-prefix: "order-service:cache:"
    caches:
      dictionary:
        local-enabled: true   # read-heavy, tolerates seconds of staleness
        local-ttl: 5m
      user-profile:
        ttl: 5m               # L2 only
```

## Configuration

| Property | Default | Description |
|---|---|---|
| `ddk.cache.enabled` | `true` | Turn off to let Spring Boot's own cache auto-configuration take over |
| `ddk.cache.default-ttl` | `30m` | L2 TTL; `0` means no expiration |
| `ddk.cache.ttl-jitter` | `0.1` | L2 TTL is randomly extended by up to this ratio so batches do not expire together |
| `ddk.cache.cache-null-values` | `true` | Cache `null` results to stop cache penetration |
| `ddk.cache.null-value-ttl` | `1m` | TTL for cached `null`, applied to both levels |
| `ddk.cache.key-prefix` | `ddk:cache:` | Redis key is `<prefix><cache>::<key>`; also prefixes the invalidation channel |
| `ddk.cache.local.enabled` | `false` | Enable L1 for every cache |
| `ddk.cache.local.ttl` | `30s` | L1 TTL, which is also the upper bound of cross-instance staleness |
| `ddk.cache.local.maximum-size` | `10000` | L1 entries per cache |
| `ddk.cache.local.broadcast-evict` | `true` | Broadcast L1 invalidation over Redis Pub/Sub |
| `ddk.cache.caches.<name>.*` | — | Per-cache `ttl`, `local-enabled`, `local-ttl`, `local-maximum-size` |

## Design decisions

- **L1 is off by default.** Each instance has its own L1, so enabling it introduces a staleness window across instances. Turn it on per cache where that trade-off is acceptable.
- **Invalidation broadcast is best effort.** Redis Pub/Sub does not redeliver messages missed during a disconnect; the L1 TTL is the final safety net.
- **Redis failures never reach business code.** L2 errors are treated as misses and counted. Caches with L1 keep serving from L1; caches without L1 call the method every time.
- **Without a `RedisConnectionFactory` bean, every cache runs on L1 only** and the application still starts.
- **Values in L2 use the Redis starter's serializer**, including its deserialization type allow-list (`ddk.redis.trusted-packages`).
- **`clear()` uses `SCAN`**, not `KEYS`.
- **`@Cacheable(sync = true)` loads once per key per instance.**

## Metrics

With Micrometer on the classpath:

| Meter | Tags |
|---|---|
| `ddk.cache.access` | `cache`, `result` = `l1_hit` / `l2_hit` / `miss` |
| `ddk.cache.remote.errors` | `cache`, `operation` = `get` / `put` / `evict` / `clear` |

Keys are never used as tags.
