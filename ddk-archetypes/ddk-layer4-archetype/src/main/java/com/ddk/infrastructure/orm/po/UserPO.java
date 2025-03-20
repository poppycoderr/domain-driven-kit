package com.ddk.infrastructure.orm.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * @author Elijah Du
 * @date 2025/2/19
 */
@Data
@TableName("user")
public class UserPO {

    @TableId(type = IdType.INPUT )
    private Long id;

    private String username;

    private String password;

    private String email;

    private String phoneNumber;

    private Boolean status;

}
