package com.ddk.core.domain;

import java.util.Collection;

/**
 * 领域事件发布器。
 * <p>
 * {@code ddk-core} 只定义契约，具体实现由基础设施层提供（例如包装
 * Spring 的 {@code ApplicationEventPublisher}）。
 *
 * <h2>发布时机：写入成功之后、事务提交之前</h2>
 * 仓储在写入成功后、仍在业务事务里时发布聚合收集的事件。订阅方按自己的需要选择执行时机，这是最容易做错的地方：
 * <ul>
 *     <li>必须和业务一起提交的，例如翻译成集成事件（经发件箱发出，必须在事务内）或标记读模型，用 {@code @EventListener}：
 *     它在同一个事务里执行，业务回滚时它的写入也一起回滚；</li>
 *     <li>只能在提交之后做、丢了也可以接受的，例如发通知，用 {@code @TransactionalEventListener(phase = AFTER_COMMIT)}：
 *     事务回滚时它不会执行，也就不会通知一个没有发生的变化。</li>
 * </ul>
 *
 * <pre>{@code
 * // 仓储保存成功后再排空事件
 * public void save(Order order) {
 *     delegate.update(order);
 *     if (order.hasDomainEvents()) {
 *         publisher.publishAll(order.drainDomainEvents());
 *     }
 * }
 * }</pre>
 *
 * @author Elijah Du
 * @date 2026/9/11
 */
public interface DomainEventPublisher {

    void publish(DomainEvent event);

    default void publishAll(Collection<? extends DomainEvent> events) {
        if (events == null) {
            return;
        }
        events.forEach(this::publish);
    }

    /**
     * 排空并发布一个聚合上累积的领域事件。
     * <p>
     * 这是仓储实现最常写的三行样板，收在这里避免每个仓储各写一遍：
     * 判空、{@code drainDomainEvents()}（复制 + 清空一步完成）、逐个发布。
     *
     * @param aggregate 可以为 null，此时什么都不做
     */
    default void publishEventsOf(AggregateRoot<?> aggregate) {
        if (aggregate != null && aggregate.hasDomainEvents()) {
            publishAll(aggregate.drainDomainEvents());
        }
    }
}
