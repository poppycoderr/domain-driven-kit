package com.ddk.archguard.fixture.job.valid.adapter.job;

import com.xxl.job.core.handler.annotation.XxlJob;
import org.springframework.scheduling.annotation.Scheduled;

public class OrderJobs {

    @Scheduled(fixedDelay = 60_000)
    public void closeExpiredOrders() {
    }

    @XxlJob("reconcileOrders")
    public void reconcile() {
    }
}
