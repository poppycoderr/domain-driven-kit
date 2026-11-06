package com.example.inventory.infrastructure.persistence;

import com.example.inventory.domain.acl.InventoryBoard;
import com.example.inventory.domain.acl.ReservationJournal;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 读模型和预留流水的 JDBC 实现。
 */
@Repository
@RequiredArgsConstructor
public class JdbcInventoryBoard implements InventoryBoard, ReservationJournal {

    private final JdbcTemplate jdbc;

    @Override
    public List<LowStock> lowStock() {
        return jdbc.query("SELECT sku_id, name, available FROM t_low_stock ORDER BY sku_id",
                (rs, row) -> new LowStock(rs.getLong(1), rs.getString(2), rs.getInt(3)));
    }

    @Override
    public List<String> reservationsOf(Long skuId) {
        return jdbc.queryForList("SELECT reservation_id FROM t_reservation_journal WHERE sku_id = ? ORDER BY reservation_id", String.class, skuId);
    }

    @Override
    public void record(String reservationId, Long skuId, int quantity) {
        jdbc.update("INSERT INTO t_reservation_journal (reservation_id, sku_id, quantity) VALUES (?, ?, ?)", reservationId, skuId, quantity);
    }
}
