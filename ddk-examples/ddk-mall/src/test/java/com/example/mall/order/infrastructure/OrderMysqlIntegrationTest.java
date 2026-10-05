package com.example.mall.order.infrastructure;

import com.ddk.core.context.Operator;
import com.ddk.core.context.OperatorContext;
import com.ddk.core.repository.ConcurrentUpdateException;
import com.ddk.test.containers.DdkContainers;
import com.example.mall.order.application.command.PlaceOrderCommand;
import com.example.mall.order.application.response.OrderResponse;
import com.example.mall.order.application.service.OrderService;
import com.example.mall.order.domain.acl.OrderRepository;
import com.example.mall.order.domain.model.Order;
import com.example.mall.order.domain.model.OrderId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

/**
 * 在真实的 MySQL 上验证：Flyway 迁移脚本可以执行，订单连同订单行整体保存和加载，乐观锁生效。
 * H2 的 MySQL 模式只是近似，方言差异要在真正的数据库上才会暴露。
 */
@Testcontainers(disabledWithoutDocker = true)
@DirtiesContext
@SpringBootTest
class OrderMysqlIntegrationTest {

    @Container
    static final MySQLContainer MYSQL = DdkContainers.mysql();

    @Autowired
    private OrderService orderService;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private TransactionTemplate transaction;

    @DynamicPropertySource
    static void mysql(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("ddk.mybatis.db-type", () -> "mysql");
    }

    /**
     * 下单会异步触发库存预占。等这些后续处理做完再结束，否则它们会撞上正在关闭的应用上下文。
     */
    @AfterEach
    void awaitFollowUpWork() {
        await().atMost(Duration.ofSeconds(20))
                .untilAsserted(() -> assertThat(jdbc.queryForList(
                        "SELECT CONCAT(LISTENER_ID, ' ', EVENT_TYPE, ' ', STATUS) FROM EVENT_PUBLICATION WHERE COMPLETION_DATE IS NULL", String.class)).isEmpty());
    }

    @Test
    void ordersRoundTripThroughMysql() {
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM flyway_schema_history WHERE success = 0", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM t_product", Integer.class)).isEqualTo(3);

        OrderResponse placed = OperatorContext.callAs(Operator.of("7"), () -> orderService.place(new PlaceOrderCommand(7L, List.of(
                new PlaceOrderCommand.Line("SKU-MONITOR", 1), new PlaceOrderCommand.Line("SKU-MOUSE", 3)))));

        OrderResponse loaded = orderService.get(7L, placed.id());
        assertThat(loaded.totalAmount()).isEqualByComparingTo("2086.00");
        assertThat(loaded.lines()).extracting(OrderResponse.Line::skuId).containsExactly("SKU-MONITOR", "SKU-MOUSE");
        assertThat(jdbc.queryForObject("SELECT create_by FROM t_order WHERE id = ?", Long.class, placed.id())).isEqualTo(7L);
    }

    @Test
    void aStaleOrderCannotOverwriteANewerOne() {
        OrderResponse placed = orderService.place(new PlaceOrderCommand(7L, List.of(new PlaceOrderCommand.Line("SKU-KEYBOARD", 1))));
        Order first = orderRepository.find(OrderId.of(placed.id())).orElseThrow();
        Order second = orderRepository.find(OrderId.of(placed.id())).orElseThrow();

        // 仓储的写操作要在事务里调用：取消订单产生的消息是在事务提交时才交给投递方的
        first.cancel("第一次");
        transaction.executeWithoutResult(status -> orderRepository.update(first));
        second.cancel("第二次");

        assertThatThrownBy(() -> transaction.executeWithoutResult(status -> orderRepository.update(second)))
                .isInstanceOf(ConcurrentUpdateException.class);
        assertThat(orderService.get(7L, placed.id()).cancelReason()).isEqualTo("第一次");
    }
}
