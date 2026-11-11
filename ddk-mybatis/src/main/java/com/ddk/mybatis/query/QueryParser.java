package com.ddk.mybatis.query;

import cn.hutool.core.util.ReflectUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.ddk.core.exception.BusinessException;
import com.ddk.core.exception.CommonError;
import com.ddk.core.page.Sort;

import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.List;
import java.util.regex.Pattern;

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
     * 排序字段只能是一个 Java 标识符：MyBatis-Plus 把列名原样拼进 {@code ORDER BY}，请求里带来的表达式会到达数据库。
     */
    private static final Pattern SORT_FIELD = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");

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
            Object fieldValue = ReflectUtil.getFieldValue(query, field);
            parseSort(wrapper, field, fieldValue);

            Query annotation = field.getAnnotation(Query.class);
            if (annotation == null) {
                continue;
            }
            parseOperator(wrapper, annotation, field, fieldValue);
        }
        return wrapper;
    }

    private static <T> void parseOperator(QueryWrapper<T> wrapper, Query annotation, Field field, Object fieldValue) {
        String column = resolveColumn(field, annotation);
        annotation.operator().apply(wrapper, column, fieldValue);
    }

    @SuppressWarnings("unchecked")
    private static <T> void parseSort(QueryWrapper<T> wrapper, Field field, Object fieldValue) {
        if (isSortList(field) && fieldValue != null) {
            for (Sort sort : (List<Sort>) fieldValue) {
                String sortField = sort.getSortField();
                if (StrUtil.isEmpty(sortField)) {
                    continue;
                }
                if (!SORT_FIELD.matcher(sortField).matches()) {
                    throw new BusinessException(CommonError.VALIDATION_ERROR);
                }
                String column = StrUtil.toUnderlineCase(sortField);
                wrapper.orderBy(StrUtil.isNotEmpty(column), "ASC".equals(sort.getSortOrder()), column);
            }
        }
    }

    private static boolean isSortList(Field field) {
        if (!List.class.isAssignableFrom(field.getType())) {
            return false;
        }
        Type genericType = field.getGenericType();
        if (!(genericType instanceof ParameterizedType parameterizedType)) {
            return false;
        }
        Type[] actualTypes = parameterizedType.getActualTypeArguments();
        return actualTypes.length == 1 && Sort.class.equals(actualTypes[0]);
    }

    private static String resolveColumn(Field field, Query annotation) {
        return StrUtil.isNotEmpty(annotation.value()) ? annotation.value() : StrUtil.toUnderlineCase(field.getName());
    }
}
