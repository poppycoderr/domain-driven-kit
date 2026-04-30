package com.ddk.mybatis.query;

import cn.hutool.core.util.ReflectUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.ddk.core.page.Sort;
import org.springframework.core.GenericTypeResolver;

import java.lang.reflect.Field;
import java.util.List;

/**
 * 查询条件解析器（MyBatis-Plus 实现）
 * <p>
 * 将标注了 {@link Query} 注解的 PageQuery 子类字段解析为 MyBatis-Plus QueryWrapper。
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
     * @return MyBatis-Plus QueryWrapper
     */
    public static <T> QueryWrapper<T> parse(Object query) {
        QueryWrapper<T> wrapper = new QueryWrapper<>();

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
        if (Sort.class.equals(argType) && fieldValue != null) {
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
