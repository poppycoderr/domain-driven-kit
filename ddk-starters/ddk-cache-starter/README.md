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

## In 30 seconds

1. Add the dependency and point Spring Boot at Redis:

   ```xml
   <dependency>
       <groupId>com.domaindrivenkit</groupId>
       <artifactId>ddk-cache-starter</artifactId>
   </dependency>
   ```

   ```yaml
   spring:
     data:
       redis:
         host: localhost
         port: 6379
   ```

2. Put `@Cacheable("dictionary")` on a read method. `@EnableCaching` is already applied.
3. Check it: call the method twice; the second call does not run the method, and `redis-cli --scan --pattern 'ddk:cache:dictionary::*'` shows the entry.

## Why a Cache, not a CacheManager

`CompositeCacheManager` routes by cache name: the first manager that knows a name serves every request, and Caffeine knows every name. Redis would never be reached. Layered lookup has to happen inside a single `Cache`, which is what `TwoLevelCache` does.

## Usage

```xml
<dependency>
    <groupId>com.domaindrivenkit</groupId>
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
| `ddk.cache.local.ttl` | `30s` | L1 TTL. It bounds how long another instance can serve an old value after an invalidation that reached Redis; see [Design decisions](#design-decisions) for when it does not |
| `ddk.cache.local.maximum-size` | `10000` | L1 entries per cache |
| `ddk.cache.local.broadcast-evict` | `true` | Broadcast L1 invalidation over Redis Pub/Sub |
| `ddk.cache.caches.<name>.*` | — | Per-cache `ttl`, `local-enabled`, `local-ttl`, `local-maximum-size` |

## Design decisions

- **L1 is off by default.** Each instance has its own L1, so enabling it introduces a staleness window across instances. Turn it on per cache where that trade-off is acceptable.
- **Invalidation broadcast is best effort.** Redis Pub/Sub does not redeliver messages missed during a disconnect; the L1 TTL is the final safety net.
- **Another transport for the broadcast.** Declare your own `CacheInvalidationPublisher` bean. DDK then neither publishes nor subscribes on its Redis channel; your receiving side clears the local level with `cacheManager.findCache(name).ifPresent(cache -> cache.evictLocal(key))`, or `clearLocal()` for a clear.
- **Redis failures never reach business code.** L2 errors are treated as misses and counted. Caches with L1 keep serving from L1; caches without L1 call the method every time.
- **A failed write to L2 is not retried.** When `@CacheEvict` or `@CachePut` runs while Redis is unreachable, the local level is updated but the old value stays in Redis. Once Redis is back, every instance reads that old value again, until the L2 TTL ends it. So the staleness bound is the L1 TTL only when the invalidation reached Redis; when it failed, it is the L2 TTL. For caches where that matters, keep the L2 TTL short and alert on `ddk.cache.remote.errors{operation="evict"}`.
- **Keys do not include the tenant.** A cache hit returns the value without running the method, so the SQL with its tenant condition never runs. With multi-tenancy, put the tenant into the key yourself, for example `@Cacheable(cacheNames = "skus", key = "T(com.ddk.core.context.OperatorContext).required().tenantId() + ':' + #skuId")`, or use a cache only for data that all tenants share.
- **Without a `RedisConnectionFactory` bean, every cache runs on L1 only** and the application still starts.
- **Values in L2 use the Redis starter's serializer**, including its deserialization type allow-list (`ddk.redis.trusted-packages`). When that serializer is missing, for example because the application excluded the Redis starter's auto-configuration, the cache builds one the same way that trusts the application's own packages, and logs a warning; it never falls back to JDK serialization.
- **`clear()` uses `SCAN`**, not `KEYS`.
- **`@Cacheable(sync = true)` loads once per key per instance.**

## Metrics

With Micrometer on the classpath:

| Meter | Tags |
|---|---|
| `ddk.cache.access` | `cache`, `result` = `l1_hit` / `l2_hit` / `miss` |
| `ddk.cache.remote.errors` | `cache`, `operation` = `get` / `put` / `evict` / `clear` |

Keys are never used as tags.

## Common errors

| Symptom | Cause | Fix |
|---|---|---|
| Startup logs `No RedisConnectionFactory found, DDK cache runs with the local level only` | No Redis connection is configured, so only the local level (L1) works and nothing is shared between instances | Configure `spring.data.redis.*`, or accept local-only caching in that environment |
| Logs `L2 cache [...] get failed, falling back` | Redis failed during an operation; the cache falls back instead of failing the call | Check Redis; the warning repeats while it is down |
| Another instance serves an old value for a while | L1 is on and the invalidation broadcast was missed or turned off | Keep `ddk.cache.local.broadcast-evict=true`; `local-ttl` bounds how long a missed invalidation can last |
| After a Redis outage, all instances serve an old value until it expires in Redis | An eviction ran while Redis was unreachable; it cleared the local level but not Redis | Evict the key again, or wait for the L2 TTL; keep the L2 TTL short for caches that must not stay stale |
| One tenant sees another tenant's cached data | The cache key does not include the tenant, and a hit skips the method and its tenant-filtered SQL | Put the tenant into the key, or do not cache tenant data |
| Startup fails: `ttl jitter must be within [0, 1]` | `ddk.cache.ttl-jitter` is out of range | Use a ratio such as `0.1` |
| A value cached with `@Cacheable` cannot be read through `RedisTemplate` | The cache uses its own serialization | Read cached values through the cache |
