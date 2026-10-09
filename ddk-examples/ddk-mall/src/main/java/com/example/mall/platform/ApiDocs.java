package com.example.mall.platform;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 接口文档按限界上下文分组：每个上下文一份文档，和代码的边界一致。错误响应与错误码清单由 DDK 的 Web starter 补进每一组。
 */
@Configuration(proxyBeanMethods = false)
public class ApiDocs {

    @Bean
    OpenAPI mallOpenApi() {
        return new OpenAPI().info(new Info().title("ddk-mall").version("1").description("订单、库存、支付三个限界上下文的接口"));
    }

    @Bean
    GroupedOpenApi orderApi() {
        return GroupedOpenApi.builder().group("order").displayName("订单").packagesToScan("com.example.mall.order").build();
    }

    @Bean
    GroupedOpenApi inventoryApi() {
        return GroupedOpenApi.builder().group("inventory").displayName("库存").packagesToScan("com.example.mall.inventory").build();
    }

    @Bean
    GroupedOpenApi paymentApi() {
        return GroupedOpenApi.builder().group("payment").displayName("支付").packagesToScan("com.example.mall.payment").build();
    }
}
