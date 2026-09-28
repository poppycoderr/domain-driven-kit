package com.ddk.core.domain;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标记需要发布到进程外（消息队列等）的领域事件，并声明它发往哪里。
 * <p>
 * 领域事件默认只在进程内传递。标上本注解后，{@code ddk-event-starter} 会把它交给 Spring Modulith 的事件外发：
 * 事件与聚合在同一个事务里登记，提交后再投递到 {@link #value()} 指定的目标，失败的投递留在发布记录里可以重投。
 * 注解是纯 JDK 的，领域层不因此依赖任何框架或消息中间件。
 *
 * <pre>{@code
 * @IntegrationEvent(value = "user-events", key = "userId")
 * public record UserRegisteredEvent(UserId userId, String username, Instant occurredOn) implements DomainEvent {
 * }
 * }</pre>
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface IntegrationEvent {

    /**
     * 投递目标：Kafka 的 topic、AMQP 的 exchange、Spring Messaging 的通道 Bean 名，取决于应用引入的外发模块。
     */
    String value();

    /**
     * 作为消息 key 的属性名，即事件上的一个无参访问方法（record 组件名）。同一个 key 的消息在 Kafka 等中间件里保持顺序。
     * 属性值是 {@link Identifier} 时取它的原始值。留空表示不设置 key。
     */
    String key() default "";
}
