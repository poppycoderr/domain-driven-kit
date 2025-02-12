package io.github.ddk.core.mapper;

import java.lang.annotation.*;

/**
 * 自定义映射器注解
 *
 * @author Elijah Du
 * @date 2025/2/11
 */
@Documented
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface EnhancedMapper {

    Class<?> left();

    Class<?> right();

    String description() default "";
}