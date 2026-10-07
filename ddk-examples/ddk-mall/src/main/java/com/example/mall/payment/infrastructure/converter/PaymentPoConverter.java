package com.example.mall.payment.infrastructure.converter;

import com.ddk.core.mapper.EnhancedMapper;
import com.ddk.core.mapper.ObjectMapper;
import com.example.mall.payment.domain.model.Payment;
import com.example.mall.payment.infrastructure.orm.po.PaymentPO;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 支付单转持久化对象。
 */
@Component
@EnhancedMapper(source = Payment.class, target = PaymentPO.class, description = "Payment -> PaymentPO")
public class PaymentPoConverter implements ObjectMapper<Payment, PaymentPO> {

    @Override
    public PaymentPO map(Payment source) {
        PaymentPO po = new PaymentPO();
        po.setId(source.id().value());
        po.setOrderId(source.orderId());
        po.setCustomerId(source.customerId());
        po.setAmount(source.amount());
        po.setStatus(source.status().name());
        po.setExpiresAt(source.expiresAt());
        po.setChannelTradeNo(source.channelTradeNo());
        po.setPaidAt(source.paidAt());
        po.setVersion(source.version());
        return po;
    }

    @Override
    public List<PaymentPO> map(List<Payment> sources) {
        return sources.stream().map(this::map).toList();
    }
}
