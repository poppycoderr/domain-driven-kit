package com.example.mall.payment.infrastructure.orm.po;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;

/**
 * 支付单表的持久化对象。
 */
@Data
@TableName("t_payment")
public class PaymentPO {

    @TableId(type = IdType.INPUT)
    private Long id;

    private Long orderId;

    private Long customerId;

    private BigDecimal amount;

    private String status;

    private Instant expiresAt;

    private String channelTradeNo;

    private Instant paidAt;

    /**
     * 对账确认渠道没有扣款时要清空它；MyBatis-Plus 默认更新时跳过 null 字段，所以这一列总是写入。
     */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Instant chargeRequestedAt;

    @Version
    private Long version;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
