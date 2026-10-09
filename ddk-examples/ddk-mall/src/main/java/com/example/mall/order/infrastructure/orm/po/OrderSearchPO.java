package com.example.mall.order.infrastructure.orm.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * 订单搜索读模型表的持久化对象。
 */
@Data
@TableName("t_order_search")
public class OrderSearchPO {

    @TableId(type = IdType.INPUT)
    private Long orderId;

    private Long customerId;

    private String status;

    private BigDecimal totalAmount;

    private Integer itemCount;

    private String productNames;

    private Instant expiresAt;

    private Instant refreshedAt;
}
