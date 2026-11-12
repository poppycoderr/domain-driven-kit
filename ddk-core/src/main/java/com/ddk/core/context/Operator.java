package com.ddk.core.context;

import org.jspecify.annotations.Nullable;

import java.util.Objects;

/**
 * 执行当前操作的人或系统。
 * <p>
 * 标识一律用字符串表示，与认证方式无关：数字型的用户 ID、租户 ID 转成字符串放进来，使用方按自己的列类型再转回去。
 * <p>
 * 1.0 之前是 record。改成 final 类，是为了冻结之后还能新增属性（例如区分本人操作和代为操作）：
 * record 的规范构造器和解构模式都属于公开 API，多一个组件就是破坏性变化。新增属性会通过新的工厂方法或 {@code with...} 方法提供。
 */
public final class Operator {

    private final String id;

    private final @Nullable String name;

    private final @Nullable String tenantId;

    /**
     * @param id       操作者标识，写入审计字段
     * @param name     显示名，可以没有
     * @param tenantId 所属租户；单租户系统里为 {@code null}
     */
    public Operator(String id, @Nullable String name, @Nullable String tenantId) {
        if (id.isBlank()) {
            throw new IllegalArgumentException("Operator id must not be blank");
        }
        this.id = id;
        this.name = name;
        this.tenantId = tenantId;
    }

    public static Operator of(String id) {
        return new Operator(id, null, null);
    }

    public static Operator of(String id, String tenantId) {
        return new Operator(id, null, tenantId);
    }

    /** 操作者标识，写入审计字段。 */
    public String id() {
        return id;
    }

    /** 显示名，可以没有。 */
    public @Nullable String name() {
        return name;
    }

    /** 所属租户；单租户系统里为 {@code null}。 */
    public @Nullable String tenantId() {
        return tenantId;
    }

    @Override
    public boolean equals(@Nullable Object o) {
        return o instanceof Operator other
                && id.equals(other.id)
                && Objects.equals(name, other.name)
                && Objects.equals(tenantId, other.tenantId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, name, tenantId);
    }

    @Override
    public String toString() {
        return "Operator[id=" + id + ", name=" + name + ", tenantId=" + tenantId + "]";
    }
}
