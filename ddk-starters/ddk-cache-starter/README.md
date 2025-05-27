# DDK Cache Starter

This starter provides a multi-level caching solution for DDK applications, integrating Caffeine (as L1 cache) and Redis (as L2 cache). It simplifies the configuration and usage of Spring's caching abstraction.

## Features

-   **Multi-Level Caching:** Automatically configures a `CompositeCacheManager` with Caffeine as Level 1 (in-memory) and Redis as Level 2 (distributed) cache if both are enabled.
-   **Flexible Configuration:**
    -   Uses Caffeine if `spring.cache.caffeine.spec` is provided.
    -   Uses Redis if `spring.data.redis.host` (or other Redis properties) are provided.
    -   Falls back to a `ConcurrentMapCacheManager` if neither is configured.
    -   Allows using only Caffeine or only Redis if desired.
-   **Dependency Management:** Brings in necessary dependencies:
    -   `spring-boot-starter-cache`
    -   `com.github.ben-manes.caffeine:caffeine`
    -   `spring-boot-starter-data-redis`
-   **Standard Spring Boot Properties:** Leverages Spring Boot's conventional caching properties for configuration.

## How to Use

1.  **Include the starter in your project's `pom.xml`:**

    ```xml
    <dependency>
        <groupId>com.ddk</groupId>
        <artifactId>ddk-cache-starter</artifactId>
        <version>${ddk.version}</version> <!-- Ensure this matches your project's ddk version -->
    </dependency>
    ```

2.  **Configure Caching Properties in `application.properties` or `application.yml`:**

    **a) For Multi-Level Cache (Caffeine L1 + Redis L2 - Recommended):**

    ```properties
    # General Spring Cache properties
    spring.cache.cache-names=cache1,cache2 # Optional: Pre-define cache names

    # Caffeine (L1) Configuration
    spring.cache.caffeine.spec=maximumSize=1000,expireAfterWrite=10m # Customize as needed

    # Redis (L2) Configuration
    spring.data.redis.host=your-redis-host
    spring.data.redis.port=6379
    # spring.data.redis.password=your-redis-password # If applicable
    # spring.cache.redis.time-to-live=60m # Default TTL for Redis cache entries
    # spring.cache.redis.key-prefix=myapp:cache: # Optional prefix for all keys
    # spring.cache.redis.use-key-prefix=true
    ```

    **b) For Caffeine Only:**

    ```properties
    spring.cache.caffeine.spec=maximumSize=500,expireAfterWrite=5m
    # Ensure no spring.data.redis.host is configured or spring.cache.type=caffeine is set.
    ```

    **c) For Redis Only:**

    ```properties
    spring.data.redis.host=your-redis-host
    spring.data.redis.port=6379
    # spring.cache.redis.time-to-live=30m
    # Ensure no spring.cache.caffeine.spec is configured or spring.cache.type=redis is set.
    ```

3.  **Use Spring Cache Annotations:**

    Apply standard Spring caching annotations to your service methods:
    -   `@EnableCaching` on one of your `@Configuration` classes (this starter already includes it in its auto-configuration, so it should be active if the starter is used).
    -   `@Cacheable("cacheName")` for caching method results.
    -   `@CachePut("cacheName")` for updating cache entries.
    -   `@CacheEvict("cacheName")` or `@CacheEvict(value="cacheName", allEntries=true)` for removing entries.

    Example:
    ```java
    import org.springframework.cache.annotation.Cacheable;
    import org.springframework.stereotype.Service;

    @Service
    public class MyDataService {

        @Cacheable(value = "myCache", key = "#id")
        public String getData(String id) {
            // Simulate fetching data
            System.out.println("Fetching data for id: " + id);
            return "Data for " + id;
        }
    }
    ```

## How Multi-Level Caching Works

When both Caffeine and Redis are configured, this starter sets up a `CompositeCacheManager`.
-   **Cache Reads (`@Cacheable`):**
    1.  Spring first checks the Caffeine (L1) cache.
    2.  If found, the value is returned immediately.
    3.  If not found in Caffeine, Spring then checks the Redis (L2) cache.
    4.  If found in Redis, the value is returned and also populated into the Caffeine (L1) cache for future fast access.
    5.  If not found in Redis, the target method is executed. Its result is stored in both Redis (L2) and Caffeine (L1), then returned.
-   **Cache Writes/Updates (`@CachePut`):**
    The method is always executed. Its result is typically stored in both Redis (L2) and Caffeine (L1).
-   **Cache Evictions (`@CacheEvict`):**
    Entries are removed from both Caffeine (L1) and Redis (L2).

This setup provides fast in-memory access with Caffeine while leveraging Redis as a larger, distributed backing cache.

## Important Notes

-   The `CacheAutoConfiguration` provided by this starter will attempt to create a `CompositeCacheManager` by default if both Caffeine and Redis configurations are detected.
-   If only one type of cache is configured (e.g., only Caffeine spec is provided, or only Redis host is provided), that specific cache manager will be configured as the primary one.
-   If neither is configured, a simple `ConcurrentMapCacheManager` will be used as a fallback.
-   Serialization: Ensure that objects stored in Redis are serializable. Spring Boot typically configures Jackson for JSON serialization with Redis, but this depends on your classpath and specific configuration.
```
