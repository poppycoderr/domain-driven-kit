package com.ddk.mybatis.query;

import java.lang.annotation.*;

/**
 * 查询条件注解（MyBatis-Plus 实现）
 * <p>
 * 标注在 PageQuery 子类的字段上，用于自动构建 QueryWrapper 查询条件。
 *
 * @author Elijah Du
 * @date 2025/2/10
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD})
public @interface Query {

    /**
     * 对应数据库字段名。如果不设置则取字段名的下划线形式作为列名。
     */
    String value() default "";

    /**
     * 查询操作符
     */
    Operator operator() default Operator.EQ;
}
