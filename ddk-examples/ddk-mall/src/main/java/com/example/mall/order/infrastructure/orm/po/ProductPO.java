package com.example.mall.order.infrastructure.orm.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 商品表的持久化对象。
 */
@Data
@TableName("t_product")
public class ProductPO {

    @TableId(type = IdType.INPUT)
    private String skuId;

    private String name;

    private BigDecimal unitPrice;
}
