package com.ddk.core.page;

import cn.hutool.core.util.ReflectUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import org.springframework.core.GenericTypeResolver;

import java.lang.reflect.Field;
import java.util.List;

/**
 * 查询条件解析器
 *
 * @author Elijah Du
 * @date 2025/2/10
 */
public class QueryParser {

    /**
     * 解析查询条件
     *
     * @param query 查询条件对象
     * @param <T>   查询条件对象类型
     * @return 查询条件包装器
     */
    public static <T> QueryWrapper<T> parse(Object query) {
        QueryWrapper<T> wrapper = new QueryWrapper<>();

        // 获取所有字段，包括父类字段
        Field[] fields = ReflectUtil.getFields(query.getClass());
        for (Field field : fields) {
            Query annotation = field.getAnnotation(Query.class);
            if (annotation == null) {
                continue;
            }
            Object fieldValue = ReflectUtil.getFieldValue(query, field);
            parseOperator(wrapper, annotation, field, fieldValue);
            parseSort(wrapper, field, fieldValue);
        }
        return wrapper;
    }

    private static <T> void parseOperator(QueryWrapper<T> wrapper, Query annotation, Field field, Object fieldValue) {
        String column = resolveColumn(field, annotation);
        annotation.operator().apply(wrapper, column, fieldValue);
    }

    @SuppressWarnings("unchecked")
    private static <T> void parseSort(QueryWrapper<T> wrapper, Field field, Object fieldValue) {
        Class<?> argType = GenericTypeResolver.resolveTypeArgument(field.getType(), List.class);
        if (Sort.class.equals(argType)) {
            for (Sort sort : (List<Sort>) fieldValue) {
                String column = StrUtil.toUnderlineCase(sort.getSortField());
                wrapper.orderBy(StrUtil.isNotEmpty(column), "ASC".equals(sort.getSortOrder()), column);
            }
        }
    }

    private static String resolveColumn(Field field, Query annotation) {
        return StrUtil.isNotEmpty(annotation.value()) ? annotation.value() : StrUtil.toUnderlineCase(field.getName());
    }
}