package io.github.ddk.core.mapper;

import org.springframework.stereotype.Component;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * @author Elijah Du
 * @date 2025/2/11
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Component
public @interface CustomMapper {

    Class<?> left();

    Class<?> right();

    String description() default "";
}