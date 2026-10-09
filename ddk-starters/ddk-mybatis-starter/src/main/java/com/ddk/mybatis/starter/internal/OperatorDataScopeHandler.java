package com.ddk.mybatis.starter.internal;

import com.baomidou.mybatisplus.extension.plugins.handler.MultiDataPermissionHandler;
import com.ddk.core.context.DataScope;
import com.ddk.core.context.Operator;
import com.ddk.core.context.OperatorContext;
import com.ddk.mybatis.starter.config.DdkMybatisProperties;
import com.ddk.mybatis.starter.datascope.DataScopeResolver;
import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.schema.Table;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 行级数据权限：操作者的数据范围取自应用提供的 {@link DataScopeResolver}，MyBatis-Plus 据此给受控表的查询、更新、删除追加条件。
 * <p>
 * 和多租户一样，取不到操作者时直接抛异常，而不是放行。只作用于配置里列出的表；个别不受限制的语句在 Mapper 方法上用
 * {@code @InterceptorIgnore(dataPermission = "true")} 显式豁免。
 * <p>
 * 条件拼成字符串再解析成表达式，而不是直接构造语法树节点：值只有两种来源，数字经过解析校验，字符串把单引号转义，
 * 这样不依赖 JSqlParser 各版本之间变动较多的节点类。
 */
public class OperatorDataScopeHandler implements MultiDataPermissionHandler {

    private static final String NOTHING = "1 = 0";

    private static final DdkMybatisProperties.DataScope.Columns DEFAULTS = new DdkMybatisProperties.DataScope.Columns();

    private final DataScopeResolver resolver;

    private final DdkMybatisProperties.DataScope properties;

    private final Set<String> tables;

    private final Map<String, DdkMybatisProperties.DataScope.Columns> columns;

    public OperatorDataScopeHandler(DataScopeResolver resolver, DdkMybatisProperties.DataScope properties) {
        this.resolver = resolver;
        this.properties = properties;
        this.tables = properties.getTables().stream().map(OperatorDataScopeHandler::normalize).collect(Collectors.toUnmodifiableSet());
        this.columns = properties.getColumns().entrySet().stream()
                .collect(Collectors.toUnmodifiableMap(entry -> normalize(entry.getKey()), Map.Entry::getValue));
    }

    @Override
    public @Nullable Expression getSqlSegment(Table table, Expression where, String mappedStatementId) {
        String name = normalize(table.getName());
        if (!tables.contains(name)) {
            return null;
        }
        Operator operator = OperatorContext.current().orElseThrow(() -> new IllegalStateException(
                "Data scope is enabled for table " + name + " but OperatorContext has no operator. Run the statement inside "
                        + "OperatorContext.runAs(...), or mark the mapper method with @InterceptorIgnore(dataPermission = \"true\")"));
        DataScope scope = resolver.resolve(operator);
        if (scope.unrestricted()) {
            return null;
        }
        String prefix = (table.getAlias() != null ? table.getAlias().getName() : table.getName()) + ".";
        DdkMybatisProperties.DataScope.Columns override = columns.getOrDefault(name, DEFAULTS);
        String groupColumn = orDefault(override.getGroupColumn(), properties.getGroupColumn());
        String ownerColumn = orDefault(override.getOwnerColumn(), properties.getOwnerColumn());
        boolean numericGroupId = orDefault(override.getNumericGroupId(), properties.isNumericGroupId());
        boolean numericOwnerId = orDefault(override.getNumericOwnerId(), properties.isNumericOwnerId());

        List<String> conditions = new ArrayList<>();
        if (!scope.groups().isEmpty() && !groupColumn.isBlank()) {
            conditions.add(prefix + groupColumn + " IN (" + scope.groups().stream().sorted()
                    .map(group -> literal(group, numericGroupId)).collect(Collectors.joining(", ")) + ")");
        }
        if (scope.own() && !ownerColumn.isBlank()) {
            conditions.add(prefix + ownerColumn + " = " + literal(operator.id(), numericOwnerId));
        }
        return parse(conditions.isEmpty() ? NOTHING : "(" + String.join(" OR ", conditions) + ")");
    }

    private static <T> T orDefault(@Nullable T value, T fallback) {
        return value != null ? value : fallback;
    }

    private static String literal(String value, boolean numeric) {
        return numeric ? String.valueOf(Long.parseLong(value)) : "'" + value.replace("'", "''") + "'";
    }

    private static Expression parse(String condition) {
        try {
            return CCJSqlParserUtil.parseCondExpression(condition);
        } catch (JSQLParserException e) {
            throw new IllegalStateException("Could not build the data scope condition: " + condition, e);
        }
    }

    private static String normalize(String tableName) {
        return tableName.replace("`", "").replace("\"", "").toLowerCase(Locale.ROOT);
    }
}
