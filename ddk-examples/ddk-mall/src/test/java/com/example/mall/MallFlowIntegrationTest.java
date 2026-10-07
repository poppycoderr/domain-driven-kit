package com.example.mall;

import com.ddk.core.context.Operator;
import com.ddk.core.context.OperatorContext;
import com.ddk.test.containers.DdkContainers;
import com.ddk.test.containers.RedisContainer;
import com.ddk.test.containers.RocketMqContainer;
import com.example.mall.inventory.adapter.messaging.payload.OrderPlacedPayload;
import com.example.mall.inventory.application.service.InventoryService;
import com.example.mall.order.application.command.PlaceOrderCommand;
import com.example.mall.order.application.response.OrderResponse;
import com.example.mall.order.application.service.OrderService;
import com.example.mall.payment.application.service.PaymentService;
import com.ddk.event.starter.consumer.IntegrationEventDispatcher;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * 整条链路跑在真实的中间件上：订单写进 MySQL，消息经事件发布记录和 RocketMQ 到达库存上下文，库存在 Redis 锁里预占，
 * 结果再经 RocketMQ 回到订单上下文。
 * <p>
 * {@code @DirtiesContext} 让应用上下文在这个类结束时就关闭，早于容器停止：否则消费者和连接池会在 JVM 退出时才关闭，
 * 那时中间件已经不在了，关闭过程只能等到超时。
 */
@Testcontainers(disabledWithoutDocker = true)
@DirtiesContext
@SpringBootTest(properties = {
        "ddk.event.local-delivery.enabled=false",
        "ddk.event.rocketmq.producer-group=mall-flow-test",
        "ddk.concurrency.enabled=true",
        "ddk.concurrency.key-prefix=mall-flow:"
})
class MallFlowIntegrationTest {

    @Container
    static final MySQLContainer MYSQL = DdkContainers.mysql();

    @Container
    static final RedisContainer REDIS = DdkContainers.redis();

    @Container
    static final RocketMqContainer ROCKETMQ = DdkContainers.rocketmq();

    @Autowired
    private OrderService orders;

    @Autowired
    private InventoryService inventory;

    @Autowired
    private PaymentService payments;

    @Autowired
    private IntegrationEventDispatcher dispatcher;

    @Autowired
    private JdbcTemplate jdbc;

    @DynamicPropertySource
    static void containers(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("ddk.mybatis.db-type", () -> "mysql");
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", REDIS::getPort);
        registry.add("ddk.event.rocketmq.name-server", ROCKETMQ::getNameServer);
    }

    @BeforeAll
    static void topics() {
        ROCKETMQ.createTopic("mall-order-events", 4);
        ROCKETMQ.createTopic("mall-inventory-events", 4);
        ROCKETMQ.createTopic("mall-payment-events", 4);
    }

    @Test
    void anOrderReservesStockThroughRocketMqAndCancellingReleasesIt() {
        int keyboards = inventory.get("SKU-KEYBOARD").reserved();
        int mice = inventory.get("SKU-MOUSE").reserved();
        OrderResponse placed = place(new PlaceOrderCommand.Line("SKU-KEYBOARD", 2), new PlaceOrderCommand.Line("SKU-MOUSE", 3));
        assertThat(placed.status()).isEqualTo("PENDING_STOCK");

        await().atMost(Duration.ofSeconds(90)).until(() -> orders.get(7L, placed.id()).status().equals("PENDING_PAYMENT"));
        assertThat(inventory.get("SKU-KEYBOARD").reserved()).isEqualTo(keyboards + 2);
        assertThat(inventory.get("SKU-MOUSE").reserved()).isEqualTo(mice + 3);
        assertThat(inventory.reservationsOf(placed.id())).extracting("status").containsExactly("RESERVED", "RESERVED");

        orders.cancel(7L, placed.id(), "不想要了");

        await().atMost(Duration.ofSeconds(60)).until(() -> inventory.reservationsOf(placed.id()).stream().allMatch(r -> r.status().equals("RELEASED")));
        assertThat(inventory.get("SKU-KEYBOARD").reserved()).isEqualTo(keyboards);
        assertThat(inventory.get("SKU-MOUSE").reserved()).isEqualTo(mice);
        assertThat(inventory.get("SKU-KEYBOARD").onHand()).isEqualTo(10);
    }

    @Test
    void payingAnOrderCompletesItAndDeductsTheReservedStock() {
        int onHand = inventory.get("SKU-MOUSE").onHand();
        int reserved = inventory.get("SKU-MOUSE").reserved();
        OrderResponse placed = place(new PlaceOrderCommand.Line("SKU-MOUSE", 4));
        await().atMost(Duration.ofSeconds(90)).until(() -> paymentStatus(placed.id()).equals("PENDING"));
        assertThat(payments.get(7L, placed.id()).amount()).isEqualByComparingTo("516.00");

        assertThat(payments.pay(7L, placed.id()).status()).isEqualTo("PAID");

        await().atMost(Duration.ofSeconds(60)).until(() -> orders.get(7L, placed.id()).status().equals("PAID"));
        await().atMost(Duration.ofSeconds(60)).until(() -> inventory.reservationsOf(placed.id()).stream().allMatch(r -> r.status().equals("CONFIRMED")));
        assertThat(inventory.get("SKU-MOUSE").onHand()).isEqualTo(onHand - 4);
        assertThat(inventory.get("SKU-MOUSE").reserved()).isEqualTo(reserved);
    }

    @Test
    void anOrderPastItsDeadlineIsClosedAndItsPaymentAndStockFollow() {
        OrderResponse placed = place(new PlaceOrderCommand.Line("SKU-MONITOR", 1));
        await().atMost(Duration.ofSeconds(90)).until(() -> paymentStatus(placed.id()).equals("PENDING"));

        assertThat(orders.closeExpired(placed.expiresAt().plusSeconds(1))).isGreaterThanOrEqualTo(1);

        assertThat(orders.get(7L, placed.id()).status()).isEqualTo("CANCELLED");
        await().atMost(Duration.ofSeconds(60)).until(() -> paymentStatus(placed.id()).equals("CLOSED"));
        await().atMost(Duration.ofSeconds(60)).until(() -> inventory.reservationsOf(placed.id()).stream().allMatch(r -> r.status().equals("RELEASED")));
    }

    @Test
    void anOrderBeyondTheStockIsCancelledByTheInventoryContext() {
        OrderResponse placed = place(new PlaceOrderCommand.Line("SKU-MONITOR", 99));

        await().atMost(Duration.ofSeconds(90)).until(() -> orders.get(7L, placed.id()).status().equals("CANCELLED"));
        assertThat(orders.get(7L, placed.id()).cancelReason()).contains("库存不足");
        assertThat(inventory.get("SKU-MONITOR").reserved()).isZero();
    }

    @Test
    void aRedeliveredOrderPlacedMessageReservesOnceAndAnswersOnce() {
        OrderPlacedPayload payload = new OrderPlacedPayload(424242L, List.of(new OrderPlacedPayload.Line("SKU-MOUSE", 5)));
        String json = "{\"orderId\":424242,\"lines\":[{\"skuId\":\"SKU-MOUSE\",\"quantity\":5}]}";
        int before = inventory.get("SKU-MOUSE").reserved();

        Map<String, String> headers = Map.of(IntegrationEventDispatcher.EVENT_ID_HEADER, "redelivered-1");
        dispatcher.dispatch("mall-inventory", "mall-order-events", "placed", headers, json);
        dispatcher.dispatch("mall-inventory", "mall-order-events", "placed", headers, json);

        assertThat(inventory.get("SKU-MOUSE").reserved()).isEqualTo(before + payload.lines().getFirst().quantity());
        // Linux 上的 MySQL 区分表名大小写，Spring Modulith 建的表是大写的
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM EVENT_PUBLICATION WHERE SERIALIZED_EVENT LIKE '%424242%'", Integer.class)).isEqualTo(1);
        inventory.release(424242L);
        assertThat(inventory.get("SKU-MOUSE").reserved()).isEqualTo(before);
    }

    @Test
    void aReservationThatArrivesAfterTheCancellationIsReleased() {
        OrderResponse placed = place(new PlaceOrderCommand.Line("SKU-KEYBOARD", 1));
        await().atMost(Duration.ofSeconds(90)).until(() -> orders.get(7L, placed.id()).status().equals("PENDING_PAYMENT"));
        int reservedWhileOpen = inventory.get("SKU-KEYBOARD").reserved();
        orders.cancel(7L, placed.id(), "不想要了");
        await().atMost(Duration.ofSeconds(60)).until(() -> inventory.get("SKU-KEYBOARD").reserved() == reservedWhileOpen - 1);

        // 模拟消息乱序：订单已经取消之后，「库存已预占」才到。订单上下文应当再发一次「已取消」，而不是悄悄忽略
        long before = cancelledMessages(placed.id());
        orders.confirmStock(placed.id());

        assertThat(orders.get(7L, placed.id()).status()).isEqualTo("CANCELLED");
        assertThat(cancelledMessages(placed.id())).isEqualTo(before + 1);
    }

    private String paymentStatus(Long orderId) {
        return jdbc.query("SELECT status FROM t_payment WHERE order_id = ?", rs -> rs.next() ? rs.getString(1) : "", orderId);
    }

    private long cancelledMessages(Long orderId) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM EVENT_PUBLICATION WHERE EVENT_TYPE LIKE '%OrderCancelledMessage' "
                + "AND SERIALIZED_EVENT LIKE ?", Long.class, "%" + orderId + "%");
    }

    private OrderResponse place(PlaceOrderCommand.Line... lines) {
        return OperatorContext.callAs(Operator.of("7"), () -> orders.place(new PlaceOrderCommand(7L, List.of(lines))));
    }
}
