package com.example.mall.payment.infrastructure.channel;

import com.example.mall.payment.domain.acl.PaymentChannel;
import com.example.mall.payment.domain.model.Payment;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * 模拟的支付渠道，不连接任何外部系统。
 * <p>
 * 金额不超过上限的扣款都成功，超过上限的被拒绝，用来演示渠道拒绝的情况。流水号由支付单标识算出，
 * 所以同一张支付单重复请求得到同一个结果，和真实渠道按商户单号去重的行为一致。
 * 接入真实渠道时替换这个类即可，领域和应用层只依赖 {@link PaymentChannel}。
 */
@Slf4j
@Component
public class SimulatedPaymentChannel implements PaymentChannel {

    private final BigDecimal declineAbove;

    public SimulatedPaymentChannel(@Value("${mall.payment.simulated.decline-above:50000}") BigDecimal declineAbove) {
        this.declineAbove = declineAbove;
    }

    @Override
    public Charge charge(Payment payment) {
        if (payment.amount().compareTo(declineAbove) > 0) {
            return Charge.declined("金额超过模拟渠道的单笔上限 " + declineAbove.toPlainString());
        }
        return Charge.approved("SIM-" + payment.id().value());
    }

    @Override
    public void refund(Payment payment) {
        log.info("Simulated refund of {} for payment {} (trade {})", payment.amount(), payment.id().value(), payment.channelTradeNo());
    }
}
