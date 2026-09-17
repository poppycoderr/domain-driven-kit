package com.ddk.redis.starter.config;

import com.ddk.redis.starter.serializer.RedisJsonMapper;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurationPackages;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializer;

import java.util.ArrayList;
import java.util.List;

/**
 * Redis 自动配置：注册 key 为字符串、value 为带类型信息 JSON 的 {@code RedisTemplate}。
 * <p>
 * 排在 Spring Boot 自己的 {@link RedisAutoConfiguration} 之前，
 * 这样同名的 {@code redisTemplate} 由这里提供，Boot 的那个（JDK 序列化）不再注册；
 * {@code stringRedisTemplate} 仍由 Boot 提供。
 * <p>
 * 反序列化只放行 JDK 常用包、应用根包和 {@code ddk.redis.trusted-packages}，
 * 细节见 {@link RedisJsonMapper}。
 *
 * @author Elijah Du
 */
@AutoConfiguration(before = RedisAutoConfiguration.class)
@ConditionalOnClass(RedisTemplate.class)
@EnableConfigurationProperties(DdkRedisProperties.class)
public class DdkRedisAutoConfiguration {

    public static final String VALUE_SERIALIZER_BEAN_NAME = "ddkRedisValueSerializer";

    /**
     * value 序列化器，带类型白名单。
     * <p>
     * 单独注册成 Bean，让同样要往 Redis 存任意对象的组件（例如 {@code ddk-cache-starter} 的二级缓存）
     * 复用同一套白名单，而不是各自再配一份、各自出一个漏洞。
     */
    @Bean
    @ConditionalOnMissingBean(name = VALUE_SERIALIZER_BEAN_NAME)
    public RedisSerializer<Object> ddkRedisValueSerializer(DdkRedisProperties properties, BeanFactory beanFactory) {
        List<String> trusted = new ArrayList<>(properties.getTrustedPackages());
        if (AutoConfigurationPackages.has(beanFactory)) {
            trusted.addAll(AutoConfigurationPackages.get(beanFactory));
        }
        return new GenericJackson2JsonRedisSerializer(RedisJsonMapper.create(trusted));
    }

    @Bean
    @ConditionalOnMissingBean(name = "redisTemplate")
    public RedisTemplate<String, Object> redisTemplate(
            RedisConnectionFactory connectionFactory,
            @Qualifier(VALUE_SERIALIZER_BEAN_NAME) RedisSerializer<Object> valueSerializer) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);
        template.setKeySerializer(RedisSerializer.string());
        template.setHashKeySerializer(RedisSerializer.string());
        template.setValueSerializer(valueSerializer);
        template.setHashValueSerializer(valueSerializer);
        return template;
    }
}
