package com.ddk.event.starter.internal;

import com.ddk.core.domain.IntegrationEvent;
import com.ddk.event.starter.config.DdkEventProperties.OutsideTransaction;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.PayloadApplicationEvent;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("事务之外发布的集成事件")
class TransactionalPublicationGuardTest {

    @IntegrationEvent("orders:paid")
    record OrderPaid(
            Long orderId
    ) {
    }

    record OrderViewed(
            Long orderId
    ) {
    }

    @AfterEach
    void clear() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
        TransactionSynchronizationManager.setActualTransactionActive(false);
    }

    @Test
    @DisplayName("默认在发布处抛出异常，消息里说明原因、做法和怎么改成只警告")
    void failsWherePublished() {
        assertThatThrownBy(() -> publish(OutsideTransaction.FAIL, new OrderPaid(42L)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(OrderPaid.class.getName())
                .hasMessageContaining("outside a transaction")
                .hasMessageContaining("ddk.event.outside-transaction=warn");
    }

    @Test
    @DisplayName("warn 与 ignore 不打断发布")
    void warnAndIgnoreLetThePublicationThrough() {
        assertThatCode(() -> publish(OutsideTransaction.WARN, new OrderPaid(42L))).doesNotThrowAnyException();
        assertThatCode(() -> publish(OutsideTransaction.IGNORE, new OrderPaid(42L))).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("事务里发布的集成事件、以及没有标注的事件不受影响")
    void transactionalPublicationsAndPlainEventsPass() {
        assertThatCode(() -> publish(OutsideTransaction.FAIL, new OrderViewed(42L))).doesNotThrowAnyException();

        TransactionSynchronizationManager.initSynchronization();
        TransactionSynchronizationManager.setActualTransactionActive(true);
        assertThatCode(() -> publish(OutsideTransaction.FAIL, new OrderPaid(42L))).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("只有事务同步、没有真实事务时同样算在事务之外")
    void synchronizationWithoutATransactionIsStillOutside() {
        TransactionSynchronizationManager.initSynchronization();

        assertThatThrownBy(() -> publish(OutsideTransaction.FAIL, new OrderPaid(42L))).isInstanceOf(IllegalStateException.class);
    }

    private void publish(OutsideTransaction mode, Object event) {
        new TransactionalPublicationGuard(mode).onApplicationEvent(new PayloadApplicationEvent<>(this, event));
    }
}
