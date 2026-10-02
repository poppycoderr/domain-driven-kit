package com.example.mall.order.infrastructure.orm.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.mall.order.infrastructure.orm.po.OrderPO;
import org.apache.ibatis.annotations.Mapper;

/**
 * OrderPO 的 Mapper。
 */
@Mapper
public interface OrderMapper extends BaseMapper<OrderPO> {
}
