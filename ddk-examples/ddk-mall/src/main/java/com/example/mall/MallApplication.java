package com.example.mall;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * 参考应用入口。订单、库存、支付三个限界上下文各占一个顶层包，部署成一个应用。
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class MallApplication {

    public static void main(String[] args) {
        SpringApplication.run(MallApplication.class, args);
    }
}
