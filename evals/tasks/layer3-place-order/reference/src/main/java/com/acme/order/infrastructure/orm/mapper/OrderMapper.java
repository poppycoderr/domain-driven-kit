package com.acme.order.infrastructure.orm.mapper;

import com.acme.order.infrastructure.orm.po.OrderPO;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 订单表 Mapper。
 */
@Mapper
public interface OrderMapper extends BaseMapper<OrderPO> {
}
