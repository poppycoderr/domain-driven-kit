package com.ddk.core.domain;

import java.util.Collection;

/**
 * 领域事件发布器。
 * <p>
 * {@code ddk-core} 只定义契约，具体实现由基础设施层提供（例如包装
 * Spring 的 {@code ApplicationEventPublisher}）。
 *
 * <h2>发布时机：必须在事务提交之后</h2>
 * 这是最容易做错的地方。事件如果在事务内发布，订阅方可能读到还未提交的数据，
 * 或者事务已经回滚而事件已经发出去了。订阅方应当用
 * {@code @TransactionalEventListener(phase = AFTER_COMMIT)} 而不是 {@code @EventListener}。
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
}
