package com.ddk.archguard.fixture.configonly.businessmethod.config;

import org.springframework.context.annotation.Configuration;

/**
 * 违规：层外的 {@code @Configuration} 类里放了业务方法。
 */
@Configuration
public class ReportConfiguration {

    public int dailyTotal(int[] amounts) {
        int total = 0;
        for (int amount : amounts) {
            total += amount;
        }
        return total;
    }
}
