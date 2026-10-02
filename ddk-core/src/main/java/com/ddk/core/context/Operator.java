package com.ddk.core.context;

import org.jspecify.annotations.Nullable;

/**
 * 执行当前操作的人或系统。
 * <p>
 * 标识一律用字符串表示，与认证方式无关：数字型的用户 ID、租户 ID 转成字符串放进来，使用方按自己的列类型再转回去。
 *
 * @param id       操作者标识，写入审计字段
 * @param name     显示名，可以没有
 * @param tenantId 所属租户；单租户系统里为 {@code null}
 */
public record Operator(
        String id,

        @Nullable String name,

        @Nullable String tenantId
) {

    public Operator {
        if (id.isBlank()) {
            throw new IllegalArgumentException("Operator id must not be blank");
        }
    }

    public static Operator of(String id) {
        return new Operator(id, null, null);
    }

    public static Operator of(String id, String tenantId) {
        return new Operator(id, null, tenantId);
    }
}
