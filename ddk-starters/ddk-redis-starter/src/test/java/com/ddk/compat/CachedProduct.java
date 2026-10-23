package com.ddk.compat;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record CachedProduct(
        String sku,
        BigDecimal price,
        Instant updatedAt,
        List<String> tags,
        String note
) {
}
