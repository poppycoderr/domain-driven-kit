package com.example.user.application.handler;

import com.example.user.domain.event.UserDisabledEvent;
import com.example.user.domain.event.UserRegisteredEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 用户领域事件订阅方。只在事务提交后执行，事务回滚时不会发出通知；此时原事务已结束，需要写库要自己开新事务。
 */
@Slf4j
@Component
public class UserEventHandler {

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void on(UserRegisteredEvent event) {
        log.info("User registered: id={}, username={}", event.userId().value(), event.username());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void on(UserDisabledEvent event) {
        log.info("User disabled: id={}, reason={}", event.userId().value(), event.reason());
    }
}
