package com.example.mall.payment.adapter;

import com.example.mall.order.adapter.job.CloseExpiredOrdersJob;
import com.example.mall.order.application.service.OrderService;
import com.example.mall.payment.application.service.PaymentService;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.cache.CacheManager;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 默认 profile 下的支付与超时关单：内存 H2，三个上下文之间的消息在进程内转发，所以跨上下文的结果都要等。
 */
@SpringBootTest
@AutoConfigureMockMvc
class PaymentApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private CacheManager cacheManager;

    @Autowired
    private CloseExpiredOrdersJob closeExpiredOrdersJob;

    @Autowired
    private OrderService orders;

    @Autowired
    private PaymentService payments;

    @BeforeEach
    void resetStock() {
        jdbc.update("DELETE FROM t_stock_reservation");
        jdbc.update("UPDATE t_stock SET on_hand = 10, reserved = 0 WHERE sku_id = 'SKU-KEYBOARD'");
        jdbc.update("UPDATE t_stock SET on_hand = 50, reserved = 0 WHERE sku_id = 'SKU-MOUSE'");
        jdbc.update("UPDATE t_stock SET on_hand = 3, reserved = 0 WHERE sku_id = 'SKU-MONITOR'");
        // 上面绕过应用直接改了表，缓存里的库存要一并清掉
        cacheManager.getCache("stock").clear();
    }

    @Test
    void payingAnOrderCompletesItAndDeductsTheStock() throws Exception {
        long orderId = placeAndAwaitPayment(7L, "{\"lines\":[{\"skuId\":\"SKU-KEYBOARD\",\"quantity\":2}]}");
        mockMvc.perform(get("/payments/orders/{id}", orderId).header("X-Customer-Id", 7))
                .andExpect(jsonPath("$.data.status").value("PENDING"))
                .andExpect(jsonPath("$.data.amount").value(798.00));

        pay(7L, orderId).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PAID"))
                .andExpect(jsonPath("$.data.channelTradeNo").isNotEmpty());

        awaitOrderStatus(orderId, 7L, "PAID");
        await().atMost(Duration.ofSeconds(10)).until(() -> stock("on_hand", "SKU-KEYBOARD") == 8);
        assertThat(stock("reserved", "SKU-KEYBOARD")).isZero();

        pay(7L, orderId).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("PAYMENT_NOT_PAYABLE"));
        mockMvc.perform(post("/orders/{id}/cancel", orderId).header("X-Customer-Id", 7).contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"付完又不想要了\"}")).andExpect(jsonPath("$.code").value("ORDER_NOT_CANCELLABLE"));
    }

    @Test
    void customersOnlyPayTheirOwnOrdersAndOnlyOnceStockIsConfirmed() throws Exception {
        long orderId = placeAndAwaitPayment(7L, "{\"lines\":[{\"skuId\":\"SKU-MOUSE\",\"quantity\":1}]}");

        pay(8L, orderId).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("PAYMENT_NOT_FOUND"));
        mockMvc.perform(get("/payments/orders/{id}", orderId).header("X-Customer-Id", 8)).andExpect(jsonPath("$.code").value("PAYMENT_NOT_FOUND"));
        pay(7L, 987654321L).andExpect(jsonPath("$.code").value("PAYMENT_NOT_FOUND"));
        mockMvc.perform(post("/payments/orders/{id}/pay", orderId)).andExpect(jsonPath("$.code").value("CUSTOMER_REQUIRED"));
    }

    @Test
    void aPaymentDeclinedByTheChannelLeavesTheOrderAwaitingPayment() throws Exception {
        jdbc.update("UPDATE t_stock SET on_hand = 1000 WHERE sku_id = 'SKU-MOUSE'");
        long orderId = placeAndAwaitPayment(7L, "{\"lines\":[{\"skuId\":\"SKU-MOUSE\",\"quantity\":400}]}");

        pay(7L, orderId).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("PAYMENT_DECLINED"));

        mockMvc.perform(get("/payments/orders/{id}", orderId).header("X-Customer-Id", 7)).andExpect(jsonPath("$.data.status").value("PENDING"));
        mockMvc.perform(get("/orders/{id}", orderId).header("X-Customer-Id", 7)).andExpect(jsonPath("$.data.status").value("PENDING_PAYMENT"));
    }

    @Test
    void cancellingAnOrderClosesItsPayment() throws Exception {
        long orderId = placeAndAwaitPayment(7L, "{\"lines\":[{\"skuId\":\"SKU-MOUSE\",\"quantity\":1}]}");

        mockMvc.perform(post("/orders/{id}/cancel", orderId).header("X-Customer-Id", 7).contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"不想要了\"}")).andExpect(jsonPath("$.data.status").value("CANCELLED"));

        awaitPaymentStatus(orderId, "CLOSED");
        pay(7L, orderId).andExpect(jsonPath("$.code").value("PAYMENT_NOT_PAYABLE"));
    }

    @Test
    void theJobClosesOrdersPastTheirDeadlineAndReleasesWhatTheyHeld() throws Exception {
        long expired = placeAndAwaitPayment(7L, "{\"lines\":[{\"skuId\":\"SKU-MONITOR\",\"quantity\":2}]}");
        long inTime = placeAndAwaitPayment(7L, "{\"lines\":[{\"skuId\":\"SKU-MOUSE\",\"quantity\":1}]}");
        jdbc.update("UPDATE t_order SET expires_at = ? WHERE id = ?", Timestamp.from(Instant.now().minusSeconds(60)), expired);

        closeExpiredOrdersJob.closeExpiredOrders();

        mockMvc.perform(get("/orders/{id}", expired).header("X-Customer-Id", 7))
                .andExpect(jsonPath("$.data.status").value("CANCELLED"))
                .andExpect(jsonPath("$.data.cancelReason").value("超过支付期限，订单已关闭"));
        mockMvc.perform(get("/orders/{id}", inTime).header("X-Customer-Id", 7)).andExpect(jsonPath("$.data.status").value("PENDING_PAYMENT"));
        await().atMost(Duration.ofSeconds(10)).until(() -> stock("reserved", "SKU-MONITOR") == 0);
        awaitPaymentStatus(expired, "CLOSED");
        assertThat(orders.closeExpired(Instant.now())).as("nothing left to close").isZero();
    }

    @Test
    void aPaymentThatCrossesACancellationIsRefunded() throws Exception {
        long orderId = placeAndAwaitPayment(7L, "{\"lines\":[{\"skuId\":\"SKU-MOUSE\",\"quantity\":1}]}");
        // 模拟「付款和取消同时发生」：支付上下文已经收了钱，订单上下文却在收到「支付已完成」之前把订单取消了
        jdbc.update("UPDATE t_order SET status = 'CANCELLED', version = version + 1 WHERE id = ?", orderId);
        pay(7L, orderId).andExpect(jsonPath("$.data.status").value("PAID"));

        // 订单上下文收到「支付已完成」时发现订单已取消，再发一次「已取消」；支付上下文据此退款
        awaitPaymentStatus(orderId, "REFUNDED");
        mockMvc.perform(get("/orders/{id}", orderId).header("X-Customer-Id", 7)).andExpect(jsonPath("$.data.status").value("CANCELLED"));

        payments.cancelForOrder(orderId);
        assertThat(paymentStatus(orderId)).as("a repeated cancellation does not refund twice").isEqualTo("REFUNDED");
    }

    private long placeAndAwaitPayment(long customerId, String body) throws Exception {
        String response = mockMvc.perform(post("/orders").header("X-Customer-Id", customerId).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.expiresAt").isNotEmpty())
                .andReturn().getResponse().getContentAsString();
        long orderId = Long.parseLong(JsonPath.read(response, "$.data.id"));
        awaitOrderStatus(orderId, customerId, "PENDING_PAYMENT");
        awaitPaymentStatus(orderId, "PENDING");
        return orderId;
    }

    private void awaitOrderStatus(long orderId, long customerId, String status) {
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> mockMvc.perform(get("/orders/{id}", orderId).header("X-Customer-Id", customerId))
                .andExpect(jsonPath("$.data.status").value(status)));
    }

    private void awaitPaymentStatus(long orderId, String status) {
        await().atMost(Duration.ofSeconds(10)).until(() -> status.equals(paymentStatus(orderId)));
    }

    private String paymentStatus(long orderId) {
        return jdbc.query("SELECT status FROM t_payment WHERE order_id = ?", rs -> rs.next() ? rs.getString(1) : null, orderId);
    }

    private int stock(String column, String skuId) {
        return jdbc.queryForObject("SELECT " + column + " FROM t_stock WHERE sku_id = ?", Integer.class, skuId);
    }

    private ResultActions pay(long customerId, long orderId) throws Exception {
        return mockMvc.perform(post("/payments/orders/{id}/pay", orderId).header("X-Customer-Id", customerId));
    }
}
