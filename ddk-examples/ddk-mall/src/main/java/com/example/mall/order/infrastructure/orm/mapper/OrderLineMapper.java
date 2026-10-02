package com.example.mall.order.infrastructure.orm.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.mall.order.infrastructure.orm.po.OrderLinePO;
import org.apache.ibatis.annotations.Mapper;

/**
 * OrderLinePO 的 Mapper。
 */
@Mapper
public interface OrderLineMapper extends BaseMapper<OrderLinePO> {
}
