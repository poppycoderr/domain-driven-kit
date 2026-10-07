package com.example.mall.order.adapter.job;

import com.example.mall.order.application.service.OrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * 关闭超过支付期限的订单。
 * <p>
 * {@code @SchedulerLock} 让多个实例里同一轮只有一个在执行。它不是正确性的前提：关单逐个订单在各自的事务里进行，
 * 靠订单的状态和乐观锁保证一个订单只关一次；锁只是避免各个实例重复扫描。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CloseExpiredOrdersJob {

    private final OrderService orderService;

    @Scheduled(fixedDelayString = "${mall.order.close-interval:30s}")
    @SchedulerLock(name = "close-expired-orders")
    public void closeExpiredOrders() {
        int closed = orderService.closeExpired(Instant.now());
        if (closed > 0) {
            log.info("Closed {} expired orders", closed);
        }
    }
}
