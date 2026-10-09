package com.ddk.concurrency.starter.internal;

import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.ClassUtils;

/**
 * 当前线程是否在事务里。spring-tx 是可选依赖，应用里没有它时一律视为不在事务里。
 */
public final class TransactionState {

    private static final boolean TRANSACTIONS_PRESENT = ClassUtils.isPresent(
            "org.springframework.transaction.support.TransactionSynchronizationManager", TransactionState.class.getClassLoader());

    private TransactionState() {
    }

    public static boolean active() {
        return TRANSACTIONS_PRESENT && Synchronizations.active();
    }

    /**
     * 单独成类：只有确认 spring-tx 在 classpath 上之后才会加载它。
     */
    private static final class Synchronizations {

        static boolean active() {
            return TransactionSynchronizationManager.isActualTransactionActive();
        }
    }
}
