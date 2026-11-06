package com.example.inventory.domain.acl;

/**
 * 预留记录的写入端口。
 */
public interface ReservationJournal {

    void record(String reservationId, Long skuId, int quantity);
}
