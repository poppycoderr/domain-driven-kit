package com.ddk.archguard.fixture.configonly.mappercall.config;

import com.ddk.archguard.fixture.configonly.mappercall.infrastructure.orm.mapper.OrderMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 违规：{@code @Bean} 方法在装配时查库。
 */
@Configuration
public class CacheConfiguration {

    @Bean
    Integer initialOrderCount(OrderMapper orders) {
        return orders.countAll();
    }
}
