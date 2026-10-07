package com.example.mall.order.application.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/**
 * 订单上下文的配置。
 *
 * @param paymentTimeout 下单后多久没有支付就关闭订单
 * @param closeBatchSize 关单任务每一轮最多处理多少个订单
 */
@ConfigurationProperties(prefix = "mall.order")
public record OrderProperties(
        @DefaultValue("30m") Duration paymentTimeout,

        @DefaultValue("100") int closeBatchSize
) {
}
