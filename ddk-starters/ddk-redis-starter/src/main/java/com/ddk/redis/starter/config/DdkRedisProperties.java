package com.ddk.redis.starter.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * Redis 配置。
 *
 * @author Elijah Du
 */
@Data
@ConfigurationProperties(prefix = DdkRedisProperties.PREFIX)
public class DdkRedisProperties {

    public static final String PREFIX = "ddk.redis";

    /**
     * 允许从 Redis 反序列化的类型所在的包，按前缀匹配。
     * <p>
     * JDK 的常用包（{@code java.lang}、{@code java.util}、{@code java.time}、{@code java.math}）
     * 和应用自己的根包（{@code @SpringBootApplication} 所在包）默认已经放行，
     * 这里只需要补充除此之外的包，例如放在独立模块里的共享 DTO。
     */
    private List<String> trustedPackages = new ArrayList<>();
}
