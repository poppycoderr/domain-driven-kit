package com.acme.order.infrastructure.orm.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;

/**
 * 订单表的持久化对象。
 */
@Data
@TableName("t_order")
public class OrderPO {

    @TableId(type = IdType.INPUT)
    private Long id;

    private Long customerId;

    private Long amount;

    private String status;

    @Version
    private Long version;
}
