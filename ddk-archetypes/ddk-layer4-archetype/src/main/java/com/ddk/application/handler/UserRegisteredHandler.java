package com.ddk.application.handler;

import com.ddk.domain.event.UserDisabledEvent;
import com.ddk.domain.event.UserRegisteredEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 领域事件订阅方。
 * <p>
 * <b>关键在于 {@code @TransactionalEventListener} 而不是 {@code @EventListener}：</b>
 * 只有事务真正提交后才会执行到这里。用 {@code @EventListener} 的话，
 * 订阅方可能读到还没提交的数据，或者事务回滚了通知却已经发出去。
 * <p>
 * 另一条纪律：订阅方里<b>不要</b>再开一个依赖同一事务的写操作——
 * 此刻事务已经提交，需要写库就自己开新事务，并考虑失败后的补偿。
 *
 * @author Elijah Du
 */
@Slf4j
@Component
public class UserRegisteredHandler {

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void on(UserRegisteredEvent event) {
        log.info("用户已注册：id={}, username={}, occurredOn={}",
                event.userId(), event.username(), event.occurredOn());
        // 真实场景：发欢迎短信、初始化积分账户、投递到消息队列
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void on(UserDisabledEvent event) {
        log.info("用户已禁用：id={}, reason={}", event.userId(), event.reason());
        // 真实场景：踢下线、撤销令牌
    }
}
