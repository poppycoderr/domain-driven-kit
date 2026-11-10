package com.ddk.archguard.fixture.configonly.valid.config;

import com.ddk.archguard.fixture.configonly.valid.application.OrderService;
import com.ddk.archguard.fixture.configonly.valid.infrastructure.OrderRepositoryImpl;
import com.ddk.archguard.fixture.configonly.valid.infrastructure.orm.mapper.OrderMapper;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 只做装配：{@code @Bean} 方法、重写接口的方法和私有辅助方法。把 Mapper 交给实现类，但自己不调用它。
 */
@Configuration
public class OrderConfiguration implements InitializingBean {

    @Bean
    OrderService orderService() {
        return new OrderService();
    }

    @Bean
    OrderRepositoryImpl orderRepository(OrderMapper orders) {
        return new OrderRepositoryImpl(orders);
    }

    @Bean
    static String orderTopic() {
        return topic("orders");
    }

    @Override
    public void afterPropertiesSet() {
    }

    private static String topic(String name) {
        return "app." + name;
    }
}
