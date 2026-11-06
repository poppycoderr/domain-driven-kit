package com.example.inventory.domain;

import com.ddk.core.exception.BusinessException;
import com.example.inventory.domain.error.InventoryError;
import com.example.inventory.domain.event.StockReserved;
import com.example.inventory.domain.model.Sku;
import com.example.inventory.domain.model.SkuId;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SkuTest {

    @Test
    void reservingTakesStockAndRaisesAnEvent() {
        Sku sku = Sku.restore(SkuId.of(1L), "apple", 10, 0L);

        sku.reserve("r-1", 4);

        assertThat(sku.available()).isEqualTo(6);
        assertThat(sku.drainDomainEvents()).singleElement().isInstanceOfSatisfying(StockReserved.class, event -> {
            assertThat(event.reservationId()).isEqualTo("r-1");
            assertThat(event.skuId()).isEqualTo(1L);
            assertThat(event.remaining()).isEqualTo(6);
        });
    }

    @Test
    void stockNeverGoesBelowZero() {
        Sku sku = Sku.restore(SkuId.of(1L), "apple", 3, 0L);

        assertThatThrownBy(() -> sku.reserve("r-1", 4)).isInstanceOfSatisfying(BusinessException.class,
                e -> assertThat(e.getErrorCode()).isEqualTo(InventoryError.OUT_OF_STOCK));
        assertThatThrownBy(() -> sku.reserve("r-2", 0)).isInstanceOfSatisfying(BusinessException.class,
                e -> assertThat(e.getErrorCode()).isEqualTo(InventoryError.INVALID_QUANTITY));
        assertThat(sku.available()).isEqualTo(3);
        assertThat(sku.hasDomainEvents()).isFalse();
    }

    @Test
    void identifiersArePositive() {
        assertThatThrownBy(() -> SkuId.of(0L)).isInstanceOf(IllegalArgumentException.class);
    }
}
