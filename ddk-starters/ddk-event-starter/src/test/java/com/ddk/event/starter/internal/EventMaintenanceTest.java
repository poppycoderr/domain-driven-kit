package com.ddk.event.starter.internal;

import com.ddk.event.starter.config.DdkEventProperties;
import com.ddk.event.starter.inbox.IdempotentConsumer;
import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.core.SimpleLock;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.modulith.events.CompletedEventPublications;
import org.springframework.modulith.events.FailedEventPublications;
import org.springframework.modulith.events.ResubmissionOptions;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("事件定时维护")
class EventMaintenanceTest {

    private final FailedEventPublications failed = mock(FailedEventPublications.class);

    private final CompletedEventPublications completed = mock(CompletedEventPublications.class);

    private final IdempotentConsumer inbox = mock(IdempotentConsumer.class);

    private final DdkEventProperties.Maintenance settings = new DdkEventProperties.Maintenance();

    @Test
    @DisplayName("一轮维护按配置重投、删除已完成的发布、清理已处理消息")
    void oneRoundDoesAllThreeSteps() {
        settings.setResubmitMinAge(Duration.ofMinutes(2));
        settings.setResubmitBatchSize(50);

        new EventMaintenance(failed, completed, inbox, settings, MaintenanceLock.NONE).runOnce();

        verify(failed).resubmit(argThat((ResubmissionOptions options) ->
                options.getMinAge().equals(Duration.ofMinutes(2)) && options.getBatchSize() == 50));
        verify(completed).deletePublicationsOlderThan(Duration.ofDays(7));
        verify(inbox).purgeOlderThan(Duration.ofDays(7));
    }

    @Test
    @DisplayName("关掉的步骤不执行；保留期为零表示不删除")
    void disabledStepsAreSkipped() {
        settings.setResubmitFailed(false);
        settings.setDeleteCompletedAfter(Duration.ZERO);
        settings.setInboxRetention(Duration.ZERO);

        new EventMaintenance(failed, completed, inbox, settings, MaintenanceLock.NONE).runOnce();

        verify(failed, never()).resubmit(any());
        verify(completed, never()).deletePublicationsOlderThan(any());
        verify(inbox, never()).purgeOlderThan(any());
    }

    @Test
    @DisplayName("一步失败不影响后面的步骤；缺少的组件直接跳过")
    void aFailingStepDoesNotStopTheOthers() {
        doThrow(new IllegalStateException("database down")).when(failed).resubmit(any());

        new EventMaintenance(failed, completed, inbox, settings, MaintenanceLock.NONE).runOnce();
        new EventMaintenance(null, null, null, settings, MaintenanceLock.NONE).runOnce();

        verify(completed).deletePublicationsOlderThan(Duration.ofDays(7));
        verify(inbox).purgeOlderThan(Duration.ofDays(7));
    }

    @Test
    @DisplayName("拿不到锁时跳过这一轮")
    void skipsTheRoundWithoutTheLock() {
        MaintenanceLock busy = new MaintenanceLock() {

            @Override
            public void run(Runnable task) {
            }

            @Override
            public boolean distributed() {
                return true;
            }
        };

        new EventMaintenance(failed, completed, inbox, settings, busy).runOnce();

        verify(failed, never()).resubmit(any());
    }

    @Test
    @DisplayName("ShedLock：有 LockProvider 时在锁里执行、别的实例持有时跳过；没有 LockProvider 时不加锁直接执行")
    void shedLockGuardsTheRound() {
        AtomicInteger runs = new AtomicInteger();
        SimpleLock held = mock(SimpleLock.class);
        LockProvider provider = mock(LockProvider.class);
        when(provider.lock(any(LockConfiguration.class))).thenReturn(Optional.of(held), Optional.empty());
        StaticListableBeanFactory beans = new StaticListableBeanFactory();
        beans.addBean("lockProvider", provider);

        ShedLockMaintenanceLock lock = new ShedLockMaintenanceLock(beans.getBeanProvider(LockProvider.class), Duration.ofMinutes(1));
        lock.run(runs::incrementAndGet);
        lock.run(runs::incrementAndGet);

        assertThat(lock.distributed()).isTrue();
        assertThat(runs).hasValue(1);
        verify(held).unlock();
        verify(provider, times(2)).lock(argThat(configuration -> configuration.getName().equals(ShedLockMaintenanceLock.LOCK_NAME)
                && configuration.getLockAtMostFor().equals(Duration.ofMinutes(10))));

        ShedLockMaintenanceLock without = new ShedLockMaintenanceLock(new StaticListableBeanFactory().getBeanProvider(LockProvider.class),
                Duration.ofMinutes(1));
        without.run(runs::incrementAndGet);
        assertThat(without.distributed()).isFalse();
        assertThat(runs).hasValue(2);
    }

    @Test
    @DisplayName("配置不合法时创建失败")
    void invalidSettingsFail() {
        settings.setInterval(Duration.ZERO);
        assertThatThrownBy(() -> new EventMaintenance(failed, completed, inbox, settings, MaintenanceLock.NONE))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ddk.event.maintenance");
    }
}
