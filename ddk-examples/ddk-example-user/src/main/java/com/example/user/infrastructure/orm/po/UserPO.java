package com.example.user.infrastructure.orm.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;

/**
 * 用户表的持久化对象。形状由表结构决定，与领域模型之间靠显式转换器连接。
 */
@Data
@TableName("t_user")
public class UserPO {

    @TableId(type = IdType.INPUT)
    private Long id;

    private String username;

    private String password;

    private Integer gender;

    private String phoneNumber;

    private String email;

    private Boolean status;

    @Version
    private Long version;
}
