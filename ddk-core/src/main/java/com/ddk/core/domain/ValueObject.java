package com.ddk.core.domain;

/**
 * 值对象标记接口。
 * <p>
 * 值对象没有身份，只有值：两个金额相同、币种相同的 {@code Money} 就是同一个东西。
 * 实现者必须满足三个约束，接口本身无法强制，只能靠约定与 ArchUnit 规则检查：
 * <ol>
 *     <li>不可变：所有字段 final，不提供 setter</li>
 *     <li>按值相等：equals / hashCode 基于全部字段</li>
 *     <li>自我校验：构造时拒绝非法状态，不存在「构造出来再检查」的中间态</li>
 * </ol>
 * <p>
 * 之所以是空接口而不是抽象类：Java 21 的 {@code record} 已经天然满足前两条，
 * 用抽象类反而挡住了 {@code record}。
 *
 * <pre>{@code
 * public record Money(BigDecimal amount, Currency currency) implements ValueObject {
 *     public Money {
 *         if (amount == null || currency == null) {
 *             throw new IllegalArgumentException("金额和币种都不能为空");
 *         }
 *     }
 * }
 * }</pre>
 *
 * @author Elijah Du
 * @date 2026/9/11
 */
public interface ValueObject {
}
