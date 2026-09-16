package com.ddk.redis.starter.config;

import com.ddk.redis.starter.config.fixture.CachedUser;
import com.ddk.redis.starter.util.RedisUtil;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurationPackage;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.data.redis.serializer.SerializationException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class DdkRedisAutoConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(DdkRedisAutoConfiguration.class, RedisAutoConfiguration.class))
            .withBean(RedisConnectionFactory.class, () -> mock(RedisConnectionFactory.class));

    private static final CachedUser USER = new CachedUser(1L, "alice", null, null);

    @Test
    void replacesBootRedisTemplateWithJsonTemplate() {
        runner.run(context -> {
            assertThat(context).hasSingleBean(RedisUtil.class);
            assertThat(context).hasSingleBean(StringRedisTemplate.class);
            RedisTemplate<?, ?> template = context.getBean("redisTemplate", RedisTemplate.class);
            assertThat(template.getKeySerializer()).isEqualTo(RedisSerializer.string());
            assertThat(template.getValueSerializer())
                    .isSameAs(context.getBean(DdkRedisAutoConfiguration.VALUE_SERIALIZER_BEAN_NAME));
        });
    }

    @Test
    void trustsApplicationPackageByDefault() {
        runner.withUserConfiguration(ApplicationPackage.class).run(context -> {
            RedisSerializer<Object> serializer = valueSerializer(context.getBean("redisTemplate", RedisTemplate.class));
            assertThat(serializer.deserialize(serializer.serialize(USER))).isEqualTo(USER);
        });
    }

    @Test
    void trustsConfiguredPackages() {
        runner.withPropertyValues("ddk.redis.trusted-packages=com.ddk.redis.starter.config.fixture").run(context -> {
            RedisSerializer<Object> serializer = valueSerializer(context.getBean("redisTemplate", RedisTemplate.class));
            assertThat(serializer.deserialize(serializer.serialize(USER))).isEqualTo(USER);
        });
    }

    @Test
    void rejectsTypesOutsideTrustedPackages() {
        runner.run(context -> {
            RedisSerializer<Object> serializer = valueSerializer(context.getBean("redisTemplate", RedisTemplate.class));
            byte[] payload = serializer.serialize(USER);
            assertThatThrownBy(() -> serializer.deserialize(payload)).isInstanceOf(SerializationException.class);
        });
    }

    @Test
    void backsOffWhenApplicationDefinesRedisTemplate() {
        runner.withUserConfiguration(CustomTemplate.class).run(context ->
                assertThat(context.getBean("redisTemplate", RedisTemplate.class).getValueSerializer())
                        .isNotInstanceOf(GenericJackson2JsonRedisSerializer.class));
    }

    @SuppressWarnings("unchecked")
    private static RedisSerializer<Object> valueSerializer(RedisTemplate<?, ?> template) {
        return (RedisSerializer<Object>) template.getValueSerializer();
    }

    @Configuration(proxyBeanMethods = false)
    @AutoConfigurationPackage
    static class ApplicationPackage {
    }

    @Configuration(proxyBeanMethods = false)
    static class CustomTemplate {

        @Bean
        RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory) {
            RedisTemplate<String, Object> template = new RedisTemplate<>();
            template.setConnectionFactory(connectionFactory);
            return template;
        }
    }
}
