package com.ddk.web.config;

import com.ddk.web.internal.ErrorCodeCatalog;
import com.ddk.web.internal.ErrorContractOpenApiCustomizer;
import com.ddk.web.properties.DdkWebProperties;
import io.swagger.v3.oas.models.OpenAPI;
import org.springdoc.core.customizers.GlobalOpenApiCustomizer;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurationPackages;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.util.ClassUtils;
import org.springframework.util.function.SingletonSupplier;

import java.util.List;

/**
 * 应用引入 springdoc 时，为接口文档补上 DDK 的错误约定：统一的错误响应与错误码清单。
 * <p>
 * 成功响应不需要处理：springdoc 会按泛型把 {@code ApiResponse<UserResponse>} 展开成带具体 {@code data} 类型的 schema。
 *
 * @author Elijah Du
 */
@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnClass({GlobalOpenApiCustomizer.class, OpenAPI.class})
@ConditionalOnProperty(prefix = "ddk.web", name = "openapi", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(DdkWebProperties.class)
public class WebOpenApiAutoConfiguration {

    /**
     * 错误码在第一次生成文档时才扫描，不拖慢启动。
     */
    @Bean
    GlobalOpenApiCustomizer ddkErrorContractOpenApiCustomizer(BeanFactory beanFactory, DdkWebProperties properties) {
        List<String> packages = AutoConfigurationPackages.has(beanFactory) ? AutoConfigurationPackages.get(beanFactory) : List.of();
        ClassLoader classLoader = ClassUtils.getDefaultClassLoader();
        return new ErrorContractOpenApiCustomizer(
                SingletonSupplier.of(() -> ErrorCodeCatalog.scan(packages, classLoader == null ? getClass().getClassLoader() : classLoader)),
                properties.isJackson() && properties.isWriteLongAsString());
    }
}
