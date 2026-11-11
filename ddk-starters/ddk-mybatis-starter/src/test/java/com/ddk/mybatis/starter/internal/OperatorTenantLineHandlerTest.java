package com.ddk.mybatis.starter.internal;

import com.ddk.core.context.Operator;
import com.ddk.core.context.OperatorContext;
import net.sf.jsqlparser.expression.LongValue;
import net.sf.jsqlparser.expression.StringValue;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("多租户：租户值进入 SQL 之前的检查")
class OperatorTenantLineHandlerTest {

    private final OperatorTenantLineHandler strings = new OperatorTenantLineHandler("tenant_id", false, Set.of());

    private final OperatorTenantLineHandler numbers = new OperatorTenantLineHandler("tenant_id", true, Set.of());

    @Test
    @DisplayName("字符串租户原样写成字面量")
    void writesStringTenantsAsLiterals() {
        String tenant = "3f2a9c1e-7b4d-4e8a-9f10-2c3d4e5f6a7b";

        Object value = OperatorContext.callAs(Operator.of("alice", tenant), strings::getTenantId);

        assertThat(value).isInstanceOf(StringValue.class).hasToString("'" + tenant + "'");
    }

    @Test
    @DisplayName("含单引号、反斜杠或控制字符的字符串租户被拒绝，不会进入 SQL")
    void rejectsStringTenantsThatCouldLeaveTheLiteral() {
        for (String tenant : List.of("x' OR '1'='1", "x\\' OR 1=1 -- ", "a\nb", "a\u0000b")) {
            assertThatThrownBy(() -> OperatorContext.callAs(Operator.of("mallory", tenant), strings::getTenantId))
                    .as(tenant)
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("cannot be used in SQL");
        }
    }

    @Test
    @DisplayName("数值租户只接受数字")
    void numericTenantsAreParsed() {
        assertThat(OperatorContext.callAs(Operator.of("alice", "42"), numbers::getTenantId)).isInstanceOf(LongValue.class).hasToString("42");
        assertThatThrownBy(() -> OperatorContext.callAs(Operator.of("mallory", "1 OR 1=1"), numbers::getTenantId))
                .isInstanceOf(NumberFormatException.class);
    }
}
