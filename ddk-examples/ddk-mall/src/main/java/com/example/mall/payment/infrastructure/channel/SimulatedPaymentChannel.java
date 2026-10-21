package com.example.mall.payment.infrastructure.channel;

import com.example.mall.payment.domain.acl.PaymentChannel;
import com.example.mall.payment.domain.model.Payment;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 模拟的支付渠道，不连接任何外部系统。
 * <p>
 * 金额不超过上限的扣款都成功，超过上限的被拒绝，用来演示渠道拒绝的情况。渠道那一侧的账本放在内存里，按支付单标识（商户单号）记录：
 * 同一张支付单重复扣款得到同一个流水号，重复退款只退一次，查询返回账本里的结果，和真实渠道按商户单号去重、可查询的行为一致。
 * 账本不跨进程、重启即丢，只用于演示；接入真实渠道时替换这个类即可，领域和应用层只依赖 {@link PaymentChannel}。
 */
@Slf4j
@Component
public class SimulatedPaymentChannel implements PaymentChannel {

    private final BigDecimal declineAbove;

    private final Map<Long, String> charged = new ConcurrentHashMap<>();

    private final Map<Long, Boolean> refunded = new ConcurrentHashMap<>();

    public SimulatedPaymentChannel(@Value("${mall.payment.simulated.decline-above:50000}") BigDecimal declineAbove) {
        this.declineAbove = declineAbove;
    }

    @Override
    public Charge charge(Payment payment) {
        if (payment.amount().compareTo(declineAbove) > 0) {
            return Charge.declined("金额超过模拟渠道的单笔上限 " + declineAbove.toPlainString());
        }
        return Charge.approved(charged.computeIfAbsent(payment.id().value(), id -> "SIM-" + id));
    }

    @Override
    public void refund(Payment payment) {
        if (refunded.putIfAbsent(payment.id().value(), Boolean.TRUE) == null) {
            log.info("Simulated refund of {} for payment {}", payment.amount(), payment.id().value());
        }
    }

    @Override
    public Lookup query(Payment payment) {
        String tradeNo = charged.get(payment.id().value());
        return tradeNo == null ? Lookup.notCharged() : Lookup.charged(tradeNo);
    }
}
