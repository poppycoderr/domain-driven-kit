package com.example.app.infrastructure.event;

import com.ddk.core.domain.DomainEvent;
import com.ddk.core.domain.DomainEventPublisher;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/**
 * 领域事件发布器的 Spring 实现。
 * <p>
 * 它只负责「把事件交给 Spring」，<b>发布时机的正确性由订阅方保证</b>——
 * 订阅方必须用 {@code @TransactionalEventListener(phase = AFTER_COMMIT)}，
 * 而不是 {@code @EventListener}，否则事务回滚了事件也已经发出去。
 * <p>
 * 目前放在示例工程里；等契约稳定后会收进 DDK 的 starter。
 *
 * @author Elijah Du
 */
@Component
@RequiredArgsConstructor
public class SpringDomainEventPublisher implements DomainEventPublisher {

    private final ApplicationEventPublisher delegate;

    @Override
    public void publish(DomainEvent event) {
        delegate.publishEvent(event);
    }
}
