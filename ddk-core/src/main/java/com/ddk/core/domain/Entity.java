package com.ddk.core.domain;

import org.jspecify.annotations.Nullable;

import java.util.Objects;

/**
 * 实体基类：按身份相等。
 * <p>
 * 实体的相等性由标识决定，而不是由字段值决定——用户改了昵称，还是同一个用户。
 * 这正是实体与 {@link ValueObject} 的分界线。
 *
 * @param <ID> 标识类型
 * @author Elijah Du
 * @date 2026/9/11
 */
public abstract class Entity<ID extends Identifier<?>> {

    /**
     * 实体标识。
     * <p>
     * 为什么不是 final：很多实体在持久化之前没有 ID（数据库自增主键）。
     * 强制 final 会导致必须提前生成 ID，那是另一种设计取向（UUID / 雪花），
     * 这里不替使用者做这个决定。
     */
    protected @Nullable ID id;

    protected Entity() {
    }

    protected Entity(ID id) {
        this.id = id;
    }

    public @Nullable ID id() {
        return id;
    }

    /**
     * 是否尚未持久化（没有标识）。
     */
    public boolean isNew() {
        return id == null;
    }

    /**
     * 由持久化适配层在写入后回填标识。
     * <p>
     * {@code protected} 而不是 public：回填是基础设施的职责，
     * 只允许子类在自己的包内开放给对应的仓储实现，避免业务代码随意改身份。
     *
     * @throws IllegalStateException 标识已存在时拒绝覆盖，身份一旦确定就不可变
     */
    protected void assignId(ID id) {
        if (this.id != null) {
            throw new IllegalStateException(
                    getClass().getSimpleName() + " 的标识已是 " + this.id + "，不允许重新赋值");
        }
        if (id == null) {
            throw new IllegalArgumentException("回填的标识不能为 null");
        }
        this.id = id;
    }

    /**
     * 标识为 null 时退化为引用相等：两个都没落库的实体不应该被判定为同一个。
     */
    @Override
    public final boolean equals(@Nullable Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        Entity<?> other = (Entity<?>) o;
        return id != null && Objects.equals(id, other.id);
    }

    /**
     * 用类型的 hashCode，而不是 id 的。
     * <p>
     * 实体入 HashSet 之后如果被回填了 ID，基于 id 的 hashCode 会变化，
     * 导致再也找不到它——那是破坏 {@code hashCode} 契约。
     * 代价是同类实体全部落在同一个桶里，哈希分布退化；
     * 但实体本来就不该被大量放进哈希集合，契约正确性优先于分布。
     */
    @Override
    public final int hashCode() {
        return getClass().hashCode();
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "(" + (id == null ? "new" : id) + ")";
    }
}
