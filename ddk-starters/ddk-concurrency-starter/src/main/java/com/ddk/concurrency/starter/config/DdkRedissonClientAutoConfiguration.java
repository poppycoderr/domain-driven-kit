package com.ddk.concurrency.starter.config;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.redisson.config.SingleServerConfig;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.data.redis.autoconfigure.DataRedisConnectionDetails;
import org.springframework.context.annotation.Bean;

/**
 * 应用没有声明 {@code RedissonClient} 时，按 Spring Boot 的 Redis 连接信息（{@code spring.data.redis.*}）创建一个单机连接。
 * <p>
 * Sentinel、Cluster 或需要自定义 TLS 的部署由应用自己声明 {@code RedissonClient}。单独成一个自动配置类，是为了排在并发控制的
 * 自动配置之前：后者按容器里是否已有 {@code RedissonClient} 决定注册什么。
 *
 * @author Elijah Du
 */
@AutoConfiguration(afterName = {
        "org.springframework.boot.data.redis.autoconfigure.DataRedisAutoConfiguration",
        "org.redisson.spring.starter.RedissonAutoConfigurationV2",
        "org.redisson.spring.starter.RedissonAutoConfigurationV4"
})
@ConditionalOnClass({RedissonClient.class, DataRedisConnectionDetails.class})
@ConditionalOnProperty(prefix = DdkConcurrencyProperties.PREFIX, name = "enabled", matchIfMissing = true)
public class DdkRedissonClientAutoConfiguration {

    @Bean(destroyMethod = "shutdown")
    @ConditionalOnMissingBean
    @ConditionalOnBean(DataRedisConnectionDetails.class)
    RedissonClient ddkRedissonClient(DataRedisConnectionDetails details) {
        DataRedisConnectionDetails.Standalone standalone = details.getStandalone();
        if (standalone == null) {
            throw new IllegalStateException("ddk-concurrency-starter creates a RedissonClient for standalone Redis only; "
                    + "declare a RedissonClient bean for Sentinel or Cluster");
        }
        Config config = new Config();
        SingleServerConfig server = config.useSingleServer()
                .setAddress((details.getSslBundle() == null ? "redis://" : "rediss://") + standalone.getHost() + ":" + standalone.getPort())
                .setDatabase(standalone.getDatabase());
        if (details.getUsername() != null) {
            server.setUsername(details.getUsername());
        }
        if (details.getPassword() != null) {
            server.setPassword(details.getPassword());
        }
        return Redisson.create(config);
    }
}
