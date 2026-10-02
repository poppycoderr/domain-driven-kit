package com.ddk.archguard.fixture.job.violation.application;

import org.springframework.scheduling.annotation.Scheduled;

public class OrderService {

    @Scheduled(cron = "0 0 * * * *")
    public void closeExpiredOrders() {
    }
}
