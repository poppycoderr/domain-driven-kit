package com.example.mall.order.infrastructure.orm.po;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 订单表的持久化对象。{@link #lines} 不是表里的列：仓储在读写订单行表之后把它装上或取出，转换器因此能把订单当作一个整体来转换。
 */
@Data
@TableName("t_order")
public class OrderPO {

    @TableId(type = IdType.INPUT)
    private Long id;

    private Long customerId;

    private String status;

    private BigDecimal totalAmount;

    private String cancelReason;

    @Version
    private Long version;

    @TableField(fill = FieldFill.INSERT)
    private Long createBy;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Long updateBy;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @TableField(exist = false)
    private List<OrderLinePO> lines = new ArrayList<>();
}
