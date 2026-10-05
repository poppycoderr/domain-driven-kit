package com.example.mall.inventory.domain;

import com.ddk.test.domain.DdkAssertions;
import com.example.mall.inventory.domain.error.InventoryError;
import com.example.mall.inventory.domain.event.StockDeductedEvent;
import com.example.mall.inventory.domain.event.StockReleasedEvent;
import com.example.mall.inventory.domain.event.StockReservedEvent;
import com.example.mall.inventory.domain.model.ReservationId;
import com.example.mall.inventory.domain.model.ReservationStatus;
import com.example.mall.inventory.domain.model.SkuId;
import com.example.mall.inventory.domain.model.Stock;
import com.example.mall.inventory.domain.model.StockReservation;
import org.junit.jupiter.api.Test;

import static com.ddk.test.domain.DdkAssertions.assertThatRejected;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StockTest {

    private static final SkuId MOUSE = SkuId.of("SKU-MOUSE");

    @Test
    void reservingKeepsGoodsOnHandButNotAvailable() {
        Stock stock = Stock.restore(MOUSE, 10, 0, 0L);

        stock.reserve(4);

        assertThat(stock.onHand()).isEqualTo(10);
        assertThat(stock.reserved()).isEqualTo(4);
        assertThat(stock.available()).isEqualTo(6);
    }

    @Test
    void reservingMoreThanAvailableIsRejectedAsAWhole() {
        Stock stock = Stock.restore(MOUSE, 10, 8, 0L);

        assertThatRejected(() -> stock.reserve(3)).withCode(InventoryError.INSUFFICIENT_STOCK).withArgs("SKU-MOUSE", 2, 3);
        assertThatRejected(() -> stock.reserve(0)).withCode(InventoryError.INVALID_QUANTITY);
        assertThat(stock.reserved()).isEqualTo(8);
    }

    @Test
    void releasingReturnsGoodsAndDeductingShipsThem() {
        Stock released = Stock.restore(MOUSE, 10, 4, 0L);
        released.release(4);
        assertThat(released.available()).isEqualTo(10);

        Stock deducted = Stock.restore(MOUSE, 10, 4, 0L);
        deducted.deduct(4);
        assertThat(deducted.onHand()).isEqualTo(6);
        assertThat(deducted.reserved()).isZero();

        assertThatThrownBy(() -> deducted.release(1)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void aReservationCanBeSettledOnlyOnce() {
        StockReservation reservation = StockReservation.reserve(ReservationId.of(1L), 100L, MOUSE, 2);
        DdkAssertions.assertThat(reservation).hasRaisedExactly(StockReservedEvent.class);
        reservation.clearEvents();

        assertThat(reservation.release()).isTrue();
        assertThat(reservation.release()).isFalse();
        assertThat(reservation.status()).isEqualTo(ReservationStatus.RELEASED);
        DdkAssertions.assertThat(reservation).hasRaisedExactly(StockReleasedEvent.class);
        assertThatRejected(reservation::confirm).withCode(InventoryError.RESERVATION_ALREADY_RELEASED);
    }

    @Test
    void aConfirmedReservationCannotBeReleased() {
        StockReservation reservation = StockReservation.reserve(ReservationId.of(1L), 100L, MOUSE, 2);
        reservation.clearEvents();

        assertThat(reservation.confirm()).isTrue();
        assertThat(reservation.confirm()).isFalse();
        DdkAssertions.assertThat(reservation).hasRaisedExactly(StockDeductedEvent.class);
        assertThatRejected(reservation::release).withCode(InventoryError.RESERVATION_ALREADY_CONFIRMED);
    }
}
