package com.ddk.event.starter.internal;

import com.ddk.core.domain.IntegrationEvent;
import com.ddk.event.starter.config.DdkEventProperties.OutsideTransaction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationListener;
import org.springframework.context.PayloadApplicationEvent;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 发现在事务之外发布的集成事件。
 * <p>
 * 集成事件靠事务送出去：发布时登记，提交之后投递。没有事务就没有「提交之后」，Spring 会跳过事务监听器，事件不会投递，
 * 而且没有任何提示；用了 Spring Modulith 时还会留下一条永远处于未完成状态的发布记录。这里在发布的那一刻检查，
 * 默认直接抛出异常，让问题出现在写错的那一行，而不是几天后发现下游少了数据。
 */
public final class TransactionalPublicationGuard implements ApplicationListener<PayloadApplicationEvent<?>> {

    private static final Logger log = LoggerFactory.getLogger(TransactionalPublicationGuard.class);

    private final OutsideTransaction mode;

    public TransactionalPublicationGuard(OutsideTransaction mode) {
        this.mode = mode;
    }

    @Override
    public void onApplicationEvent(PayloadApplicationEvent<?> published) {
        check(published.getPayload());
    }

    /**
     * @throws IllegalStateException 事件是集成事件、当前没有事务，并且处理方式是 {@code FAIL}
     */
    public void check(Object event) {
        Class<?> type = event.getClass();
        if (mode == OutsideTransaction.IGNORE || !type.isAnnotationPresent(IntegrationEvent.class) || inTransaction()) {
            return;
        }
        String message = type.getName() + " was published outside a transaction and will not be delivered: "
                + "publish integration events inside the transaction that changes the data, for example from a @Transactional service method";
        if (mode == OutsideTransaction.FAIL) {
            throw new IllegalStateException(message + ". Set ddk.event.outside-transaction=warn to log instead");
        }
        log.warn(message);
    }

    private static boolean inTransaction() {
        return TransactionSynchronizationManager.isSynchronizationActive() && TransactionSynchronizationManager.isActualTransactionActive();
    }
}
