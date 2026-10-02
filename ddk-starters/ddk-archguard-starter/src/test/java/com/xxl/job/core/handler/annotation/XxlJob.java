package com.xxl.job.core.handler.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 测试替身：只为让规则按全限定名匹配到 {@code @XxlJob}，不引入 XXL-Job 本身。
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface XxlJob {

    String value();
}
