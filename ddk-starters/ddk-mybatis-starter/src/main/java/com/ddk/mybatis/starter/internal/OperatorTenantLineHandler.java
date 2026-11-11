package com.ddk.mybatis.starter.internal;

import com.baomidou.mybatisplus.extension.plugins.handler.TenantLineHandler;
import com.ddk.core.context.Operator;
import com.ddk.core.context.OperatorContext;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.LongValue;
import net.sf.jsqlparser.expression.StringValue;

import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 多租户：租户取自 {@link OperatorContext}，MyBatis-Plus 据此给每条 SQL 追加租户条件。
 * <p>
 * 没有操作者或操作者没有租户时直接抛异常，而不是放行：少了租户条件的查询会读到所有租户的数据，这类错误必须在开发阶段就暴露。
 * 确实不分租户的表列在 {@code ignore-tables} 里；个别跨租户的语句在 Mapper 方法上用
 * {@code @InterceptorIgnore(tenantLine = "true")} 显式豁免。
 */
public class OperatorTenantLineHandler implements TenantLineHandler {

    private final String column;

    private final boolean numericId;

    private final Set<String> ignoredTables;

    public OperatorTenantLineHandler(String column, boolean numericId, Set<String> ignoredTables) {
        this.column = column;
        this.numericId = numericId;
        this.ignoredTables = ignoredTables.stream().map(OperatorTenantLineHandler::normalize).collect(Collectors.toUnmodifiableSet());
    }

    @Override
    public Expression getTenantId() {
        String tenantId = OperatorContext.current().map(Operator::tenantId).orElse(null);
        if (tenantId == null || tenantId.isBlank()) {
            throw new IllegalStateException("Multi-tenancy is enabled but OperatorContext has no tenant. Run the statement inside "
                    + "OperatorContext.runAs(...), list the table in ddk.mybatis.tenant.ignore-tables, or mark the mapper method with "
                    + "@InterceptorIgnore(tenantLine = \"true\")");
        }
        return numericId ? new LongValue(Long.parseLong(tenantId)) : new StringValue(checkedForSql(tenantId));
    }

    /**
     * 字符串租户直接写进 SQL 字面量，JSqlParser 的 {@link StringValue} 不转义。单引号会结束字面量，MySQL 默认还把反斜杠当转义符，
     * 只转义引号不够，所以含这些字符和控制字符的租户值一律拒绝。租户值可能来自网关透传的请求头或没有签名的消息头。
     */
    private static String checkedForSql(String tenantId) {
        for (int i = 0; i < tenantId.length(); i++) {
            char c = tenantId.charAt(i);
            if (c == '\'' || c == '\\' || Character.isISOControl(c)) {
                throw new IllegalStateException("Tenant id contains a quote, a backslash or a control character and cannot be used in SQL");
            }
        }
        return tenantId;
    }

    @Override
    public String getTenantIdColumn() {
        return column;
    }

    @Override
    public boolean ignoreTable(String tableName) {
        return ignoredTables.contains(normalize(tableName));
    }

    private static String normalize(String tableName) {
        return tableName.replace("`", "").replace("\"", "").toLowerCase(Locale.ROOT);
    }
}
