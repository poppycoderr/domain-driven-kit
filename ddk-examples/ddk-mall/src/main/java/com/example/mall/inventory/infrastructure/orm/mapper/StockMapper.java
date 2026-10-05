package com.example.mall.inventory.infrastructure.orm.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.mall.inventory.infrastructure.orm.po.StockPO;
import org.apache.ibatis.annotations.Mapper;

/**
 * StockPO 的 Mapper。
 */
@Mapper
public interface StockMapper extends BaseMapper<StockPO> {
}
