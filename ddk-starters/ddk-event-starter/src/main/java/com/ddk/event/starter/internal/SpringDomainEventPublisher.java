package com.ddk.event.starter.internal;

import com.ddk.core.domain.DomainEvent;
import com.ddk.core.domain.DomainEventPublisher;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;

/**
 * {@link DomainEventPublisher} 的 Spring 实现。
 * <p>
 * 它只负责把事件交给 Spring 的事件总线，在仓储写入成功后、业务事务提交之前执行。订阅方选择执行时机：
 * 必须和业务一起提交的（翻译成集成事件、标记读模型）用 {@code @EventListener}，在同一个事务里执行；
 * 只能在提交之后做的通知用 {@code @TransactionalEventListener(phase = AFTER_COMMIT)}，事务回滚时不会执行。
 * 见 {@link DomainEventPublisher}。
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
