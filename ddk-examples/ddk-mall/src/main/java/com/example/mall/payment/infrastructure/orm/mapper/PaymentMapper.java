package com.example.mall.payment.infrastructure.orm.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.mall.payment.infrastructure.orm.po.PaymentPO;
import org.apache.ibatis.annotations.Mapper;

/**
 * PaymentPO 的 Mapper。
 */
@Mapper
public interface PaymentMapper extends BaseMapper<PaymentPO> {
}
