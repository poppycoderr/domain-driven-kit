package com.example.mall.order.infrastructure.orm.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 订单行表的持久化对象。
 */
@Data
@TableName("t_order_line")
public class OrderLinePO {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long orderId;

    private Integer lineNo;

    private String skuId;

    private String productName;

    private BigDecimal unitPrice;

    private Integer quantity;
}
