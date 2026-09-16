package com.ddk.cache.starter.config;

import com.ddk.cache.starter.invalidation.CacheInvalidationListener;
import com.ddk.cache.starter.invalidation.CacheInvalidationPublisher;
import com.ddk.cache.starter.invalidation.RedisCacheInvalidationPublisher;
import com.ddk.cache.starter.metrics.CacheMetrics;
import com.ddk.cache.starter.metrics.MicrometerCacheMetrics;
import com.ddk.cache.starter.support.DdkCacheManager;
import com.ddk.cache.starter.support.JitteredTtlFunction;
import com.ddk.redis.starter.config.DdkRedisAutoConfiguration;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.cache.CacheAutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.BatchStrategies;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.cache.RedisCacheWriter;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.data.redis.serializer.RedisSerializationContext.SerializationPair;
import org.springframework.data.redis.serializer.RedisSerializer;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * 二级缓存自动配置。
 * <p>
 * 顺序：在 Redis 相关自动配置之后（要用到连接工厂和值序列化器），
 * 在 Spring Boot 的 {@link CacheAutoConfiguration} 之前（它在没有 {@code CacheManager} 时才生效，谁先注册谁说了算）。
 * <p>
 * 容器里没有 {@link RedisConnectionFactory} 时降级为纯本地缓存，应用照常启动。
 *
 * @author Elijah Du
 */
@Slf4j
@AutoConfiguration(
        after = {RedisAutoConfiguration.class, DdkRedisAutoConfiguration.class},
        before = CacheAutoConfiguration.class)
@ConditionalOnClass({CacheManager.class, Caffeine.class})
@ConditionalOnProperty(prefix = DdkCacheProperties.PREFIX, name = "enabled", matchIfMissing = true)
@EnableConfigurationProperties(DdkCacheProperties.class)
@EnableCaching
public class DdkCacheAutoConfiguration {

    /**
     * 失效广播消息只含字符串，用一个不带类型信息的独立 mapper。
     */
    private static final ObjectMapper MESSAGE_MAPPER = JsonMapper.builder().build();

    @Bean
    @ConditionalOnMissingBean(CacheManager.class)
    public DdkCacheManager cacheManager(DdkCacheProperties properties,
                                        ObjectProvider<RedisConnectionFactory> connectionFactory,
                                        @Qualifier(DdkRedisAutoConfiguration.VALUE_SERIALIZER_BEAN_NAME)
                                        ObjectProvider<RedisSerializer<Object>> valueSerializer,
                                        ObjectProvider<CacheInvalidationPublisher> publisher,
                                        ObjectProvider<CacheMetrics> metrics) {
        RedisConnectionFactory factory = connectionFactory.getIfAvailable();
        Function<String, Cache> remote = null;
        if (factory == null) {
            log.warn("No RedisConnectionFactory found, DDK cache runs with the local level only");
        } else {
            remote = redisCacheFactory(factory, properties, valueSerializer.getIfAvailable(RedisSerializer::java));
        }
        return new DdkCacheManager(properties, remote,
                RedisCacheConfiguration.defaultCacheConfig().getConversionService(),
                publisher.getIfAvailable(() -> CacheInvalidationPublisher.NOOP),
                metrics.getIfAvailable(() -> CacheMetrics.NOOP));
    }

    private static Function<String, Cache> redisCacheFactory(RedisConnectionFactory factory,
                                                             DdkCacheProperties properties,
                                                             RedisSerializer<Object> valueSerializer) {
        RedisCacheConfiguration base = RedisCacheConfiguration.defaultCacheConfig()
                .computePrefixWith(name -> properties.getKeyPrefix() + name + "::")
                .serializeKeysWith(SerializationPair.fromSerializer(RedisSerializer.string()))
                .serializeValuesWith(SerializationPair.fromSerializer(valueSerializer));
        if (!properties.isCacheNullValues()) {
            base = base.disableCachingNullValues();
        }

        Map<String, RedisCacheConfiguration> perCache = new HashMap<>();
        for (Map.Entry<String, DdkCacheProperties.CacheSpec> entry : properties.getCaches().entrySet()) {
            if (entry.getValue().getTtl() != null) {
                perCache.put(entry.getKey(), base.entryTtl(ttl(properties, entry.getValue().getTtl())));
            }
        }

        // SCAN 代替 KEYS 清空缓存，避免大 key 空间下阻塞 Redis
        RedisCacheWriter writer = RedisCacheWriter.nonLockingRedisCacheWriter(factory, BatchStrategies.scan(1000));
        RedisCacheManager redis = RedisCacheManager.builder(writer)
                .cacheDefaults(base.entryTtl(ttl(properties, properties.getDefaultTtl())))
                .withInitialCacheConfigurations(perCache)
                .build();
        redis.afterPropertiesSet();
        return redis::getCache;
    }

    private static JitteredTtlFunction ttl(DdkCacheProperties properties, Duration ttl) {
        return new JitteredTtlFunction(ttl, properties.getTtlJitter(), properties.getNullValueTtl());
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(MeterRegistry.class)
    static class MetricsConfiguration {

        @Bean
        @ConditionalOnMissingBean
        CacheMetrics ddkCacheMetrics(ObjectProvider<MeterRegistry> registry) {
            return new MicrometerCacheMetrics(registry);
        }
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnBean(RedisConnectionFactory.class)
    @Conditional(OnBroadcastEvictCondition.class)
    static class InvalidationConfiguration {

        @Bean
        @ConditionalOnMissingBean(CacheInvalidationPublisher.class)
        RedisCacheInvalidationPublisher ddkCacheInvalidationPublisher(RedisConnectionFactory factory,
                                                                      DdkCacheProperties properties) {
            return new RedisCacheInvalidationPublisher(new StringRedisTemplate(factory), MESSAGE_MAPPER,
                    properties.getKeyPrefix() + "invalidation");
        }

        @Bean
        RedisMessageListenerContainer ddkCacheInvalidationListenerContainer(
                RedisConnectionFactory factory,
                RedisCacheInvalidationPublisher publisher,
                ObjectProvider<DdkCacheManager> cacheManager) {
            RedisMessageListenerContainer container = new RedisMessageListenerContainer();
            container.setConnectionFactory(factory);
            cacheManager.ifAvailable(manager -> container.addMessageListener(
                    new CacheInvalidationListener(manager, MESSAGE_MAPPER, publisher.origin()),
                    new ChannelTopic(publisher.channel())));
            return container;
        }
    }
}
