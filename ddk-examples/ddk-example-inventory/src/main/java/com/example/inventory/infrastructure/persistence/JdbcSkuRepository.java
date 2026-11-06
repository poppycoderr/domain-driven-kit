package com.example.inventory.infrastructure.persistence;

import com.ddk.core.repository.ConcurrentUpdateException;
import com.example.inventory.domain.acl.SkuRepository;
import com.example.inventory.domain.model.Sku;
import com.example.inventory.domain.model.SkuId;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.Objects;
import java.util.Optional;

/**
 * 商品的 JDBC 存取，按版本号做乐观锁。
 */
@Repository
@RequiredArgsConstructor
public class JdbcSkuRepository implements SkuRepository {

    private final JdbcTemplate jdbc;

    @Override
    public Optional<Sku> find(SkuId id) {
        return jdbc.query("SELECT id, name, available, version FROM t_sku WHERE id = ?",
                (rs, row) -> Sku.restore(SkuId.of(rs.getLong(1)), rs.getString(2), rs.getInt(3), rs.getLong(4)), id.value()).stream().findFirst();
    }

    @Override
    public void update(Sku sku) {
        long version = Objects.requireNonNull(sku.version());
        int updated = jdbc.update("UPDATE t_sku SET available = ?, version = version + 1 WHERE id = ? AND version = ?",
                sku.available(), sku.skuId().value(), version);
        if (updated == 0) {
            throw new ConcurrentUpdateException("Sku " + sku.skuId().value());
        }
    }
}
