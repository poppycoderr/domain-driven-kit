package com.example.mall.inventory.infrastructure.orm.po;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 预占记录表的持久化对象。
 */
@Data
@TableName("t_stock_reservation")
public class StockReservationPO {

    @TableId(type = IdType.INPUT)
    private Long id;

    private Long orderId;

    private String skuId;

    private Integer quantity;

    private String status;

    @Version
    private Long version;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
