package com.example.mall.order.infrastructure.orm.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.mall.order.infrastructure.orm.po.ProductPO;
import org.apache.ibatis.annotations.Mapper;

/**
 * ProductPO 的 Mapper。
 */
@Mapper
public interface ProductMapper extends BaseMapper<ProductPO> {
}
