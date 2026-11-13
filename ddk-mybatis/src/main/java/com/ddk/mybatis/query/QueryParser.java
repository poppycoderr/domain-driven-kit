package com.ddk.mybatis.query;

import cn.hutool.core.util.ReflectUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableFieldInfo;
import com.baomidou.mybatisplus.core.metadata.TableInfo;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.ddk.core.exception.BusinessException;
import com.ddk.core.exception.CommonError;
import com.ddk.core.page.Sort;
import org.jspecify.annotations.Nullable;

import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
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
        return parse(query, null);
    }

    /**
     * 解析查询条件，排序字段限定在持久化对象的列上
     * <p>
     * 排序字段可以写属性名（{@code createTime}）或列名（{@code create_time}），按 MyBatis-Plus 的表信息换成列名，所以
     * {@code @TableField} 改过名的列也能排序。不是这张表的列、或者标了 {@code @TableField(select = false)} 的列，
     * 以 {@link CommonError#VALIDATION_ERROR} 拒绝：前者会到数据库才失败，后者不在响应里，按它排序能推断出它的大小。
     * 表信息还没有初始化时（没有对应的 Mapper），退回 {@link #parse(Object)} 的规则。
     *
     * @param query       查询条件对象
     * @param entityClass 持久化对象的类型，为 {@code null} 时同 {@link #parse(Object)}
     * @param <T>         持久化对象类型
     * @return MyBatis-Plus QueryWrapper
     */
    public static <T> QueryWrapper<T> parse(Object query, @Nullable Class<T> entityClass) {
        QueryWrapper<T> wrapper = new QueryWrapper<>();
        TableInfo table = entityClass != null ? TableInfoHelper.getTableInfo(entityClass) : null;

        Field[] fields = ReflectUtil.getFields(query.getClass());
        for (Field field : fields) {
            Object fieldValue = ReflectUtil.getFieldValue(query, field);
            parseSort(wrapper, field, fieldValue, table);

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
    private static <T> void parseSort(QueryWrapper<T> wrapper, Field field, Object fieldValue, @Nullable TableInfo table) {
        if (isSortList(field) && fieldValue != null) {
            for (Sort sort : (List<Sort>) fieldValue) {
                String sortField = sort.getSortField();
                if (StrUtil.isEmpty(sortField)) {
                    continue;
                }
                if (!SORT_FIELD.matcher(sortField).matches()) {
                    throw new BusinessException(CommonError.VALIDATION_ERROR);
                }
                String column = table != null
                        ? sortableColumn(table, sortField).orElseThrow(() -> new BusinessException(CommonError.VALIDATION_ERROR))
                        : StrUtil.toUnderlineCase(sortField);
                wrapper.orderBy(StrUtil.isNotEmpty(column), "ASC".equals(sort.getSortOrder()), column);
            }
        }
    }

    private static Optional<String> sortableColumn(TableInfo table, String sortField) {
        if (table.havePK() && matches(sortField, table.getKeyProperty(), table.getKeyColumn())) {
            return Optional.of(table.getKeyColumn());
        }
        return table.getFieldList().stream()
                .filter(TableFieldInfo::isSelect)
                .filter(column -> matches(sortField, column.getProperty(), column.getColumn()))
                .map(TableFieldInfo::getColumn)
                .findFirst();
    }

    private static boolean matches(String sortField, String property, String column) {
        return sortField.equals(property) || sortField.toLowerCase(Locale.ROOT).equals(column.toLowerCase(Locale.ROOT));
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
