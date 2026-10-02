package com.ddk.archguard.fixture.joblock.violation.adapter.job;

import org.springframework.scheduling.annotation.Scheduled;

public class OrderJobs {

    @Scheduled(fixedDelay = 60_000)
    public void closeExpiredOrders() {
    }
}
