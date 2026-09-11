package com.ddk.core.domain;

import java.io.Serializable;

/**
 * 类型化标识。
 * <p>
 * 把「用户 ID」从 {@code Long} 提升成 {@code UserId}，让参数传反这类 bug 在编译期暴露：
 *
 * <pre>{@code
 * // 用 Long：能编译、能运行、能上线
 * void transfer(Long fromUserId, Long toAccountId);
 * transfer(accountId, userId);              // 编译器无话可说
 *
 * // 用 Identifier：编译期报错
 * void transfer(UserId from, AccountId to);
 * transfer(accountId, userId);              // 编译不过
 * }</pre>
 * <p>
 * 子类推荐写法：私有构造器 + 静态工厂 + 构造期校验。
 *
 * <pre>{@code
 * public final class UserId extends Identifier<Long> {
 *     private UserId(Long value) {
 *         super(value);
 *         if (value <= 0) {
 *             throw new IllegalArgumentException("UserId 必须为正数，实际为 " + value);
 *         }
 *     }
 *
 *     public static UserId of(Long value) {
 *         return new UserId(value);
 *     }
 * }
 * }</pre>
 *
 * @param <T> 标识的原始值类型，必须可序列化，以便跨进程传递与持久化
 * @author Elijah Du
 * @date 2026/9/11
 */
public abstract class Identifier<T extends Serializable> implements ValueObject, Serializable {

    private static final long serialVersionUID = 1L;

    private final T value;

    protected Identifier(T value) {
        if (value == null) {
            throw new IllegalArgumentException(getClass().getSimpleName() + " 的值不能为 null");
        }
        this.value = value;
    }

    /**
     * 取出原始值。仅用于持久化与外部协议边界，领域代码内部应直接传递 Identifier 本身。
     */
    public T value() {
        return value;
    }

    /**
     * 按「具体类型 + 值」相等。
     * <p>
     * 这里必须比较 {@code getClass()} 而不是 {@code instanceof Identifier}：
     * 否则 {@code UserId.of(1L).equals(OrderId.of(1L))} 会返回 true，类型化标识就白做了。
     */
    @Override
    public final boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        return value.equals(((Identifier<?>) o).value);
    }

    @Override
    public final int hashCode() {
        return value.hashCode();
    }

    @Override
    public String toString() {
        return String.valueOf(value);
    }
}
