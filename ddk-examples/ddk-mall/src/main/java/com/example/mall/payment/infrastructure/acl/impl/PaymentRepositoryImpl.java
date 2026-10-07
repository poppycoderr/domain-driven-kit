package com.example.mall.payment.infrastructure.acl.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.ddk.mybatis.repository.GenericRepositoryImpl;
import com.example.mall.payment.domain.acl.PaymentRepository;
import com.example.mall.payment.domain.model.Payment;
import com.example.mall.payment.domain.model.PaymentId;
import com.example.mall.payment.infrastructure.orm.mapper.PaymentMapper;
import com.example.mall.payment.infrastructure.orm.po.PaymentPO;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * 支付单仓储实现。
 */
@Repository
public class PaymentRepositoryImpl extends GenericRepositoryImpl<Payment, PaymentId, PaymentPO, PaymentMapper> implements PaymentRepository {

    @Override
    public Optional<Payment> findByOrder(Long orderId) {
        return Optional.ofNullable(getBaseMapper().selectOne(Wrappers.lambdaQuery(PaymentPO.class).eq(PaymentPO::getOrderId, orderId)))
                .map(toEntity()::map);
    }
}
