package com.ddk.concurrency.starter.integration.other;

import com.ddk.concurrency.starter.Idempotent;

/**
 * 和集成测试里的 {@code OrderService} 同名、在另一个包里：默认作用域不能让两者共用登记。
 */
public class OrderService {

    @Idempotent(key = "#requestId")
    public String submit(String requestId) {
        return "other";
    }
}
