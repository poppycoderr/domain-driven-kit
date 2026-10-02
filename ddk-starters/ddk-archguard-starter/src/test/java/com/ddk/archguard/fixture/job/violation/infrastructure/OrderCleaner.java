package com.ddk.archguard.fixture.job.violation.infrastructure;

import com.xxl.job.core.handler.annotation.XxlJob;

public class OrderCleaner {

    @XxlJob("purgeOrders")
    public void purge() {
    }
}
