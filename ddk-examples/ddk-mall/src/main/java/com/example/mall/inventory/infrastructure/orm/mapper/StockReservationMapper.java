package com.example.mall.inventory.infrastructure.orm.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.mall.inventory.infrastructure.orm.po.StockReservationPO;
import org.apache.ibatis.annotations.Mapper;

/**
 * StockReservationPO 的 Mapper。
 */
@Mapper
public interface StockReservationMapper extends BaseMapper<StockReservationPO> {
}
