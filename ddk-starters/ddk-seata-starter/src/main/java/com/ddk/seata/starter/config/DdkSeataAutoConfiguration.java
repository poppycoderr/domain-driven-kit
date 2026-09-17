package com.ddk.seata.starter.config;

import com.ddk.seata.starter.http.XidPropagationInterceptor;
import org.apache.seata.core.context.RootContext;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.restclient.RestClientCustomizer;
import org.springframework.boot.restclient.RestTemplateCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestTemplate;

/**
 * DDK 对 Seata 的补充自动配置：出站 HTTP 调用传播全局事务 XID。
 * <p>
 * 数据源代理、{@code @GlobalTransactional} 扫描、入站 XID 绑定都由 {@code seata-spring-boot-starter} 提供。
 * 拦截器通过 Spring Boot 的 customizer 挂到容器提供的 {@code RestClient.Builder} 与 {@code RestTemplateBuilder} 上，
 * 应用自己 {@code new RestTemplate()} 得到的实例不受影响。
 *
 * @author Elijah Du
 */
@AutoConfiguration
@ConditionalOnClass(RootContext.class)
@ConditionalOnProperty(prefix = "seata", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(DdkSeataProperties.class)
public class DdkSeataAutoConfiguration {

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(RestClient.class)
    @ConditionalOnProperty(prefix = DdkSeataProperties.PREFIX, name = "http-propagation.enabled",
            havingValue = "true", matchIfMissing = true)
    static class HttpPropagationConfiguration {

        private final XidPropagationInterceptor interceptor = new XidPropagationInterceptor();

        @Bean
        RestClientCustomizer ddkSeataRestClientCustomizer() {
            return builder -> builder.requestInterceptor(interceptor);
        }

        @Bean
        @ConditionalOnClass(RestTemplate.class)
        RestTemplateCustomizer ddkSeataRestTemplateCustomizer() {
            return restTemplate -> restTemplate.getInterceptors().add(interceptor);
        }
    }
}
