package com.example.mall.payment.adapter.job;

import com.example.mall.payment.application.service.PaymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;

/**
 * 和支付渠道对账：处理扣款之后没能记账或退款的支付单。
 * <p>
 * 只看请求扣款已经超过 {@code mall.payment.reconcile-after} 的，给正在进行的支付留出时间。{@code @SchedulerLock} 避免各个实例重复扫描；
 * 它不是正确性的前提，结算靠支付单的状态和乐观锁，渠道按支付单标识去重。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReconcilePaymentsJob {

    private static final int BATCH = 100;

    private final PaymentService paymentService;

    @Value("${mall.payment.reconcile-after:2m}")
    private Duration reconcileAfter;

    @Scheduled(fixedDelayString = "${mall.payment.reconcile-interval:1m}")
    @SchedulerLock(name = "reconcile-payments")
    public void reconcile() {
        int reconciled = paymentService.reconcile(Instant.now().minus(reconcileAfter), BATCH);
        if (reconciled > 0) {
            log.info("Reconciled {} payments with the channel", reconciled);
        }
    }
}
