package com.ddk.core.domain;

import java.util.Objects;

/**
 * 规格：把业务规则表达成有名字、可组合、可单测的领域概念。
 * <p>
 * 复杂判定如果直接写成 Service 里的一串 if，规则就没有名字，也无法复用。
 * 规格把它变成一等公民：
 *
 * <pre>{@code
 * Specification<User> newActiveUser =
 *         new ActiveUser().and(new RegisteredWithin(Duration.ofDays(7)));
 *
 * if (newActiveUser.isSatisfiedBy(user)) { ... }
 * }</pre>
 *
 * <h2>边界：这是内存判定，不会翻译成 SQL</h2>
 * 它的适用场景是「对已加载的聚合校验规则」，不要拿它去过滤全表——
 * 那需要在 {@code ddk-mybatis} 里另做一层能下推到数据库的实现。
 *
 * @param <T> 被判定的对象类型
 * @author Elijah Du
 * @date 2026/9/11
 */
@FunctionalInterface
public interface Specification<T> {

    boolean isSatisfiedBy(T candidate);

    default Specification<T> and(Specification<T> other) {
        Objects.requireNonNull(other, "组合的规格不能为 null");
        return candidate -> this.isSatisfiedBy(candidate) && other.isSatisfiedBy(candidate);
    }

    default Specification<T> or(Specification<T> other) {
        Objects.requireNonNull(other, "组合的规格不能为 null");
        return candidate -> this.isSatisfiedBy(candidate) || other.isSatisfiedBy(candidate);
    }

    default Specification<T> not() {
        return candidate -> !this.isSatisfiedBy(candidate);
    }

    /**
     * 恒真规格，作为 {@code and} 归约的初始值。
     */
    static <T> Specification<T> any() {
        return candidate -> true;
    }

    /**
     * 恒假规格，作为 {@code or} 归约的初始值。
     */
    static <T> Specification<T> none() {
        return candidate -> false;
    }
}
