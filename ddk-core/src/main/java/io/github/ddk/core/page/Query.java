package io.github.ddk.core.page;

import java.lang.annotation.*;

/**
 * 查询条件注解
 *
 * @author Elijah Du
 * @date 2025/2/10
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD})
public @interface Query {

    /**
     * 默认属性，对应数据库字段名
     * 如果不设置则取字段名作为列名
     */
    String value() default "";

    /**
     * 查询操作符
     */
    Operator operator() default Operator.EQ;
}