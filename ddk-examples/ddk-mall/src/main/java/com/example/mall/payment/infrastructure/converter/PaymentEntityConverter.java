package com.example.mall.payment.infrastructure.converter;

import com.ddk.core.mapper.EnhancedMapper;
import com.ddk.core.mapper.ObjectMapper;
import com.example.mall.payment.domain.model.Payment;
import com.example.mall.payment.domain.model.PaymentId;
import com.example.mall.payment.domain.model.PaymentStatus;
import com.example.mall.payment.infrastructure.orm.po.PaymentPO;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 持久化对象转支付单。
 */
@Component
@EnhancedMapper(source = PaymentPO.class, target = Payment.class, description = "PaymentPO -> Payment")
public class PaymentEntityConverter implements ObjectMapper<PaymentPO, Payment> {

    @Override
    public Payment map(PaymentPO source) {
        return Payment.restore(PaymentId.of(source.getId()), source.getOrderId(), source.getCustomerId(), source.getAmount(),
                PaymentStatus.valueOf(source.getStatus()), source.getExpiresAt(), source.getChannelTradeNo(), source.getPaidAt(),
                source.getVersion());
    }

    @Override
    public List<Payment> map(List<PaymentPO> sources) {
        return sources.stream().map(this::map).toList();
    }
}
