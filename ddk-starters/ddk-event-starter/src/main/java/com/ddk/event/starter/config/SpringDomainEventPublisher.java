package com.ddk.event.starter.config;

import com.ddk.core.domain.DomainEvent;
import com.ddk.core.domain.DomainEventPublisher;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;

/**
 * {@link DomainEventPublisher} 的 Spring 实现。
 * <p>
 * 它只负责「把事件交给 Spring 的事件总线」，<b>发布时机的正确性由订阅方保证</b>：
 * 订阅方必须用 {@code @TransactionalEventListener(phase = AFTER_COMMIT)}，
 * 而不是 {@code @EventListener}——否则事务回滚了事件也已经发出去，
 * 或者订阅方读到还没提交的数据。
 *
 * @author Elijah Du
 */
@Slf4j
public class SpringDomainEventPublisher implements DomainEventPublisher {

    private final ApplicationEventPublisher delegate;

    public SpringDomainEventPublisher(ApplicationEventPublisher delegate) {
        this.delegate = delegate;
    }

    @Override
    public void publish(DomainEvent event) {
        if (event == null) {
            return;
        }
        log.debug("Publishing domain event {} occurred at {}", event.eventType(), event.occurredOn());
        delegate.publishEvent(event);
    }
}
