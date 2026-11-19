package com.example.mall.payment.infrastructure.id;

import com.baomidou.mybatisplus.core.incrementer.IdentifierGenerator;
import com.example.mall.payment.domain.acl.PaymentIdGenerator;
import com.example.mall.payment.domain.model.PaymentId;
import org.springframework.stereotype.Component;

/**
 * 雪花算法生成支付单标识，用 DDK MyBatis starter 注册的 {@code IdentifierGenerator}，和 {@code ASSIGN_ID} 主键共用一个发号器。
 * 多实例部署时为每个实例设置不同的 {@code ddk.mybatis.worker-id} 和 {@code datacenter-id}。
 */
@Component
public class SnowflakePaymentIdGenerator implements PaymentIdGenerator {

    private final IdentifierGenerator idGenerator;

    public SnowflakePaymentIdGenerator(IdentifierGenerator idGenerator) {
        this.idGenerator = idGenerator;
    }

    @Override
    public PaymentId nextId() {
        return PaymentId.of(idGenerator.nextId(null).longValue());
    }
}
