package com.ddk.archguard.fixture.four.appinfra.application;

import com.ddk.archguard.fixture.four.appinfra.infrastructure.OrderRepositoryImpl;

/**
 * 应用服务直接持有基础设施的实现类，而不是领域层的端口。
 */
public class OrderService {

    OrderRepositoryImpl repository;
}
