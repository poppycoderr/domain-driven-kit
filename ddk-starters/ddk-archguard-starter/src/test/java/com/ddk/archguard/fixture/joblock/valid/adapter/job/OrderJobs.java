package com.ddk.archguard.fixture.joblock.valid.adapter.job;

import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;

public class OrderJobs {

    @Scheduled(fixedDelay = 60_000)
    @SchedulerLock(name = "closeExpiredOrders")
    public void closeExpiredOrders() {
    }
}
