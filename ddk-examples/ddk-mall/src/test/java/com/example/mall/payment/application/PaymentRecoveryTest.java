package com.example.mall.payment.application;

import com.ddk.core.exception.BusinessException;
import com.example.mall.order.application.service.OrderService;
import com.example.mall.payment.application.response.PaymentResponse;
import com.example.mall.payment.application.service.PaymentService;
import com.example.mall.payment.domain.model.Payment;
import com.example.mall.payment.infrastructure.channel.SimulatedPaymentChannel;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 支付的故障注入：渠道扣款成功之后本地没能结算、扣款期间订单被取消、渠道拒绝。对账之后，每一笔扣款要么记成已支付，要么退回，
 * 且只退一次。
 */
@SpringBootTest(properties = "mall.payment.reconcile-interval=1h")
@AutoConfigureMockMvc
class PaymentRecoveryTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private PaymentService payments;

    @Autowired
    private OrderService orders;

    @Autowired
    private FaultyChannel channel;

    @BeforeEach
    void reset() {
        channel.reset();
        jdbc.update("UPDATE t_stock SET on_hand = 50, reserved = 0 WHERE sku_id = 'SKU-MOUSE'");
    }

    @Test
    void aChargeWhoseSettlementWasLostIsRecordedByReconciliation() throws Exception {
        long orderId = placeAndAwaitPayment(31L);
        channel.failAfterCharge.set(true);

        assertThatThrownBy(() -> payments.pay(31L, orderId)).hasMessageContaining("connection reset after charging");
        assertThat(paymentStatus(orderId)).isEqualTo("PENDING");
        assertThat(chargeRequestedAt(orderId)).as("the attempt was committed before the charge").isNotNull();

        assertThat(payments.reconcile(Instant.now().plusSeconds(1), 100)).isPositive();

        assertThat(paymentStatus(orderId)).isEqualTo("PAID");
        awaitOrderStatus(orderId, 31L, "PAID");
        assertThat(chargeRequestedAt(orderId)).as("kept as a record of the charge").isNotNull();
        payments.reconcile(Instant.now().plusSeconds(1), 100);
        assertThat(paymentStatus(orderId)).as("a paid payment is not reconciled again").isEqualTo("PAID");
    }

    @Test
    void anOrderCancelledDuringTheChargeIsRefundedOnce() throws Exception {
        long orderId = placeAndAwaitPayment(32L);
        channel.pause();

        CompletableFuture<PaymentResponse> paying = CompletableFuture.supplyAsync(() -> payments.pay(32L, orderId));
        channel.awaitCharging();
        orders.cancel(32L, orderId, "顾客改主意了");
        await().atMost(Duration.ofSeconds(10)).until(() -> "CLOSED".equals(paymentStatus(orderId)));
        channel.resume();

        assertThat(paying.get(10, TimeUnit.SECONDS).status()).isEqualTo("REFUNDED");
        assertThat(paymentStatus(orderId)).isEqualTo("REFUNDED");
        assertThat(channel.refunds).hasValue(1);
        payments.reconcile(Instant.now().plusSeconds(1), 100);
        assertThat(channel.refunds).hasValue(1);
    }

    @Test
    void aCancelledOrderWhoseRefundWasLostIsRefundedByReconciliationOnlyOnce() throws Exception {
        long orderId = placeAndAwaitPayment(33L);
        channel.pause();
        channel.failAfterCharge.set(true);

        CompletableFuture<PaymentResponse> paying = CompletableFuture.supplyAsync(() -> payments.pay(33L, orderId));
        channel.awaitCharging();
        orders.cancel(33L, orderId, "顾客改主意了");
        await().atMost(Duration.ofSeconds(10)).until(() -> "CLOSED".equals(paymentStatus(orderId)));
        channel.resume();
        assertThat(paying).failsWithin(Duration.ofSeconds(10));
        assertThat(channel.refunds).hasValue(0);

        payments.reconcile(Instant.now().plusSeconds(1), 100);
        payments.reconcile(Instant.now().plusSeconds(1), 100);

        assertThat(paymentStatus(orderId)).isEqualTo("REFUNDED");
        assertThat(channel.refunds).hasValue(1);
    }

    @Test
    void aDeclinedChargeIsClearedByReconciliationAndCanBePaidAgain() throws Exception {
        long orderId = placeAndAwaitPayment(34L);
        channel.decline.set(true);

        assertThatThrownBy(() -> payments.pay(34L, orderId)).isInstanceOf(BusinessException.class).hasMessageContaining("测试渠道拒绝");
        assertThat(chargeRequestedAt(orderId)).isNotNull();

        payments.reconcile(Instant.now().plusSeconds(1), 100);
        assertThat(chargeRequestedAt(orderId)).as("the channel never charged it").isNull();
        assertThat(paymentStatus(orderId)).isEqualTo("PENDING");

        channel.decline.set(false);
        assertThat(payments.pay(34L, orderId).status()).isEqualTo("PAID");
    }

    private long placeAndAwaitPayment(long customerId) throws Exception {
        String response = mockMvc.perform(post("/orders").header("X-Customer-Id", customerId).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"lines\":[{\"skuId\":\"SKU-MOUSE\",\"quantity\":1}]}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        long orderId = Long.parseLong(JsonPath.read(response, "$.data.id"));
        awaitOrderStatus(orderId, customerId, "PENDING_PAYMENT");
        await().atMost(Duration.ofSeconds(10)).until(() -> "PENDING".equals(paymentStatus(orderId)));
        return orderId;
    }

    private void awaitOrderStatus(long orderId, long customerId, String status) {
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> mockMvc.perform(get("/orders/{id}", orderId).header("X-Customer-Id", customerId))
                .andExpect(jsonPath("$.data.status").value(status)));
    }

    private String paymentStatus(long orderId) {
        return jdbc.query("SELECT status FROM t_payment WHERE order_id = ?", (rs, row) -> rs.getString(1), orderId).stream().findFirst().orElse(null);
    }

    private Object chargeRequestedAt(long orderId) {
        return jdbc.queryForObject("SELECT charge_requested_at FROM t_payment WHERE order_id = ?", Object.class, orderId);
    }

    /**
     * 在模拟渠道的账本之上注入故障：扣款之后抛异常（模拟扣款成功、响应丢失或进程崩溃），扣款时停住（制造并发取消），直接拒绝。
     */
    static class FaultyChannel extends SimulatedPaymentChannel {

        final AtomicBoolean failAfterCharge = new AtomicBoolean();

        final AtomicBoolean decline = new AtomicBoolean();

        final AtomicInteger refunds = new AtomicInteger();

        private volatile CountDownLatch charging = new CountDownLatch(0);

        private volatile CountDownLatch resume = new CountDownLatch(0);

        FaultyChannel() {
            super(new BigDecimal("50000"));
        }

        void reset() {
            failAfterCharge.set(false);
            decline.set(false);
            refunds.set(0);
            charging = new CountDownLatch(0);
            resume = new CountDownLatch(0);
        }

        void pause() {
            charging = new CountDownLatch(1);
            resume = new CountDownLatch(1);
        }

        void awaitCharging() throws InterruptedException {
            assertThat(charging.await(10, TimeUnit.SECONDS)).isTrue();
        }

        void resume() {
            resume.countDown();
        }

        @Override
        public Charge charge(Payment payment) {
            if (decline.get()) {
                return Charge.declined("测试渠道拒绝");
            }
            Charge charge = super.charge(payment);
            charging.countDown();
            try {
                resume.await(10, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            if (failAfterCharge.get()) {
                throw new IllegalStateException("connection reset after charging");
            }
            return charge;
        }

        @Override
        public void refund(Payment payment) {
            refunds.incrementAndGet();
            super.refund(payment);
        }
    }

    @TestConfiguration
    static class Channels {

        @Bean
        @Primary
        FaultyChannel faultyChannel() {
            return new FaultyChannel();
        }
    }
}
