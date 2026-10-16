package com.ddk.archguard.fixture.four.wiring.config;

import com.ddk.archguard.fixture.four.wiring.application.OrderService;
import com.ddk.archguard.fixture.four.wiring.infrastructure.OrderRepositoryImpl;
import org.springframework.context.annotation.Configuration;

/**
 * 装配放在分层之外的包里：它可以同时看到应用服务和基础设施的实现。
 */
@Configuration
public class OrderConfiguration {

    OrderService orderService;

    OrderRepositoryImpl repository;
}
