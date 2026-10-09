package com.example.mall.order.infrastructure.orm.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.mall.order.infrastructure.orm.po.OrderSearchPO;
import org.apache.ibatis.annotations.Mapper;

/**
 * OrderSearchPO 的 Mapper。
 */
@Mapper
public interface OrderSearchMapper extends BaseMapper<OrderSearchPO> {
}
