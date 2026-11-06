package com.example.inventory.application.handler;

import com.ddk.event.starter.inbox.IdempotentConsumer;
import com.example.inventory.domain.acl.ReservationJournal;
import com.example.inventory.domain.event.StockReserved;
import lombok.RequiredArgsConstructor;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/**
 * 预留提交之后记一笔流水。事件先由 Spring Modulith 登记，监听器失败时可以重新投递，所以按预留号去重。
 */
@Component
@RequiredArgsConstructor
public class ReservationJournalHandler {

    private final IdempotentConsumer inbox;
    private final ReservationJournal journal;

    @ApplicationModuleListener
    public void on(StockReserved event) {
        inbox.handle("inventory.reservation-journal", event.reservationId(),
                () -> journal.record(event.reservationId(), event.skuId(), event.quantity()));
    }
}
