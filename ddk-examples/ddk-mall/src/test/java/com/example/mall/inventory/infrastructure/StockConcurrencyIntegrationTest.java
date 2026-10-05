package com.example.mall.inventory.infrastructure;

import com.ddk.core.exception.BusinessException;
import com.ddk.test.containers.DdkContainers;
import com.ddk.test.containers.RedisContainer;
import com.example.mall.inventory.application.command.ReserveStockCommand;
import com.example.mall.inventory.application.service.InventoryService;
import com.example.mall.inventory.domain.error.InventoryError;
import org.junit.jupiter.api.Test;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 在真实的 MySQL 和 Redis 上验证不超卖：很多订单同时抢少量库存，成功预占的数量恰好等于库存。
 */
@Testcontainers(disabledWithoutDocker = true)
@DirtiesContext
@SpringBootTest(properties = {"ddk.concurrency.enabled=true", "ddk.concurrency.key-prefix=mall-test:", "ddk.concurrency.lock.wait-time=20s"})
class StockConcurrencyIntegrationTest {

    @Container
    static final MySQLContainer MYSQL = DdkContainers.mysql();

    @Container
    static final RedisContainer REDIS = DdkContainers.redis();

    @Autowired
    private InventoryService inventory;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private RedissonClient redisson;

    @DynamicPropertySource
    static void containers(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("ddk.mybatis.db-type", () -> "mysql");
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", REDIS::getPort);
    }

    @Test
    void concurrentOrdersNeverOversell() throws Exception {
        int orders = 20;
        AtomicInteger reserved = new AtomicInteger();
        AtomicInteger soldOut = new AtomicInteger();
        AtomicInteger unexpected = new AtomicInteger();
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(orders);
        ExecutorService executor = Executors.newFixedThreadPool(orders);
        try {
            for (int i = 0; i < orders; i++) {
                long orderId = 5000 + i;
                executor.execute(() -> {
                    try {
                        start.await();
                        inventory.reserve(new ReserveStockCommand(orderId, List.of(new ReserveStockCommand.Line("SKU-MONITOR", 1))));
                        reserved.incrementAndGet();
                    } catch (BusinessException e) {
                        if (e.getErrorCode() == InventoryError.INSUFFICIENT_STOCK) {
                            soldOut.incrementAndGet();
                        } else {
                            unexpected.incrementAndGet();
                        }
                    } catch (Exception e) {
                        unexpected.incrementAndGet();
                    } finally {
                        done.countDown();
                    }
                });
            }
            start.countDown();
            assertThat(done.await(60, TimeUnit.SECONDS)).isTrue();
        } finally {
            executor.shutdownNow();
        }

        assertThat(unexpected).hasValue(0);
        assertThat(reserved).hasValue(3);
        assertThat(soldOut).hasValue(17);
        assertThat(jdbc.queryForObject("SELECT reserved FROM t_stock WHERE sku_id = 'SKU-MONITOR'", Integer.class)).isEqualTo(3);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM t_stock_reservation WHERE sku_id = 'SKU-MONITOR'", Integer.class)).isEqualTo(3);
        assertThat(redisson.getLock("mall-test:lock:sku:SKU-MONITOR").isLocked()).isFalse();
    }
}
