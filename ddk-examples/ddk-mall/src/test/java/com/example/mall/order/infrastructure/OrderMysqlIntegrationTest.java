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
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 在真实的 MySQL 上验证：Flyway 迁移脚本可以执行，订单连同订单行整体保存和加载，乐观锁生效。
 * H2 的 MySQL 模式只是近似，方言差异要在真正的数据库上才会暴露。
 */
@Testcontainers(disabledWithoutDocker = true)
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

    @DynamicPropertySource
    static void mysql(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("ddk.mybatis.db-type", () -> "mysql");
    }

    @Test
    void ordersRoundTripThroughMysql() {
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM flyway_schema_history WHERE success = 1", Integer.class)).isEqualTo(2);

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

        first.cancel("第一次");
        orderRepository.update(first);
        second.cancel("第二次");

        assertThatThrownBy(() -> orderRepository.update(second)).isInstanceOf(ConcurrentUpdateException.class);
        assertThat(orderService.get(7L, placed.id()).cancelReason()).isEqualTo("第一次");
    }
}
