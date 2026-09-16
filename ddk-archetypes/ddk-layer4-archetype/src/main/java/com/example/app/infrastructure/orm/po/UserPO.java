package com.example.app.infrastructure.orm.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;

/**
 * 用户持久化对象。
 * <p>
 * PO 是数据库表的镜像，不是领域模型：它是贫血的、可变的、带框架注解的，
 * 这些特征对 PO 来说全是正确的。领域模型的形状由业务决定，PO 的形状由表决定，
 * 两者之间靠显式的 Mapper 连接。
 *
 * @author Elijah Du
 * @date 2025/2/19
 */
@Data
@TableName("user")
public class UserPO {

    /**
     * 标识由领域层的 UserIdGenerator 预先生成，所以是 INPUT 而不是 AUTO——
     * 数据库不再参与身份的分配。
     */
    @TableId(type = IdType.INPUT)
    private Long id;

    private String username;

    private String password;

    /** 对应领域模型的 Gender 枚举，落库存 int */
    private Integer gender;

    private String email;

    private String phoneNumber;

    private Boolean status;

    /** 乐观锁版本号，对应 AggregateRoot.version() */
    @Version
    private Long version;
}
