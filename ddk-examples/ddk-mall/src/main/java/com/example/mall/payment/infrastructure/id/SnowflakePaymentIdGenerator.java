package com.example.mall.payment.infrastructure.id;

import cn.hutool.core.util.IdUtil;
import com.example.mall.payment.domain.acl.PaymentIdGenerator;
import com.example.mall.payment.domain.model.PaymentId;
import org.springframework.stereotype.Component;

/**
 * 雪花算法生成支付单标识。
 */
@Component
public class SnowflakePaymentIdGenerator implements PaymentIdGenerator {

    @Override
    public PaymentId nextId() {
        return PaymentId.of(IdUtil.getSnowflakeNextId());
    }
}
