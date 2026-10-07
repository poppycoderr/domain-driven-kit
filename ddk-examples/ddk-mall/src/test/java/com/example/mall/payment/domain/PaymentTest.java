package com.example.mall.payment.domain;

import com.ddk.test.domain.DdkAssertions;
import com.example.mall.payment.domain.acl.PaymentChannel;
import com.example.mall.payment.domain.error.PaymentError;
import com.example.mall.payment.domain.event.PaymentCompletedEvent;
import com.example.mall.payment.domain.model.Payment;
import com.example.mall.payment.domain.model.PaymentId;
import com.example.mall.payment.domain.model.PaymentStatus;
import com.example.mall.payment.infrastructure.channel.SimulatedPaymentChannel;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

import static com.ddk.test.domain.DdkAssertions.assertThatRejected;
import static org.assertj.core.api.Assertions.assertThat;

class PaymentTest {

    private static final Instant DEADLINE = Instant.parse("2030-01-01T00:30:00Z");

    private static final Instant IN_TIME = DEADLINE.minusSeconds(60);

    @Test
    void completingAPendingPaymentRecordsTheTradeAndTheEvent() {
        Payment payment = pending("399.00");
        DdkAssertions.assertThat(payment).hasRaisedNoEvents();

        payment.complete("SIM-1", IN_TIME);

        assertThat(payment.status()).isEqualTo(PaymentStatus.PAID);
        assertThat(payment.isPaid()).isTrue();
        assertThat(payment.channelTradeNo()).isEqualTo("SIM-1");
        assertThat(payment.paidAt()).isEqualTo(IN_TIME);
        DdkAssertions.assertThat(payment).hasRaisedExactly(PaymentCompletedEvent.class)
                .hasRaised(PaymentCompletedEvent.class, event -> {
                    assertThat(event.orderId()).isEqualTo(1001L);
                    assertThat(event.amount()).isEqualByComparingTo("399");
                });
    }

    @Test
    void aPaymentCannotBePaidTwiceNorAfterItsDeadline() {
        Payment paid = pending("399.00");
        paid.complete("SIM-1", IN_TIME);
        paid.clearEvents();
        assertThatRejected(() -> paid.complete("SIM-1", IN_TIME)).withCode(PaymentError.PAYMENT_NOT_PAYABLE).withoutRaisingEventsOn(paid);

        Payment late = pending("399.00");
        assertThatRejected(() -> late.ensurePayable(DEADLINE)).withCode(PaymentError.PAYMENT_EXPIRED).withArgs(1001L);
        assertThat(late.status()).isEqualTo(PaymentStatus.PENDING);
    }

    @Test
    void closingOnlyAffectsAPendingPaymentAndClosedOnesCannotBePaid() {
        Payment payment = pending("399.00");

        assertThat(payment.close()).isTrue();
        assertThat(payment.close()).as("a repeated cancellation changes nothing").isFalse();
        assertThat(payment.status()).isEqualTo(PaymentStatus.CLOSED);
        assertThatRejected(() -> payment.ensurePayable(IN_TIME)).withCode(PaymentError.PAYMENT_NOT_PAYABLE);
        assertThatRejected(payment::markRefunded).withCode(PaymentError.PAYMENT_NOT_REFUNDABLE);
    }

    @Test
    void onlyAPaidPaymentIsRefunded() {
        Payment payment = pending("399.00");
        payment.complete("SIM-1", IN_TIME);

        assertThat(payment.close()).as("money was taken, closing is not enough").isFalse();
        payment.markRefunded();

        assertThat(payment.status()).isEqualTo(PaymentStatus.REFUNDED);
        assertThat(payment.isPaid()).isFalse();
    }

    @Test
    void theSimulatedChannelAnswersTheSameForTheSamePaymentAndDeclinesLargeAmounts() {
        PaymentChannel channel = new SimulatedPaymentChannel(new BigDecimal("1000"));

        PaymentChannel.Charge first = channel.charge(pending("1000.00"));
        PaymentChannel.Charge again = channel.charge(pending("1000.00"));
        PaymentChannel.Charge declined = channel.charge(pending("1000.01"));

        assertThat(first.approved()).isTrue();
        assertThat(again.tradeNo()).isEqualTo(first.tradeNo()).isEqualTo("SIM-1");
        assertThat(declined.approved()).isFalse();
        assertThat(declined.reason()).contains("1000");
    }

    private static Payment pending(String amount) {
        return Payment.request(PaymentId.of(1L), 1001L, 7L, new BigDecimal(amount), DEADLINE);
    }
}
