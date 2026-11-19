package com.ddk.cache.starter.support;

import com.ddk.core.context.Operator;
import com.ddk.core.context.OperatorContext;

/**
 * 按租户区分的缓存 key 前缀 {@code @<租户>:}，租户取自 {@link OperatorContext}。
 * <p>
 * 没有租户时直接失败，而不是落到一个共用的条目上：缓存返回的是数据，少了租户的读写会把一个租户的数据交给另一个租户。
 * 租户里的冒号会和分隔符混淆，例如租户 {@code a} 的 key {@code b:c} 与租户 {@code a:b} 的 key {@code c}，所以一律拒绝。
 */
final class TenantKeys {

    private TenantKeys() {
    }

    static String prefix(String cacheName) {
        String tenantId = OperatorContext.current().map(Operator::tenantId).orElse(null);
        if (tenantId == null || tenantId.isBlank()) {
            throw new IllegalStateException("Cache " + cacheName + " is tenant-scoped but OperatorContext has no tenant. Run the call inside "
                    + "OperatorContext.runAs(...), or set ddk.cache.caches." + cacheName + ".tenant-scoped=false for data shared by all tenants");
        }
        for (int i = 0; i < tenantId.length(); i++) {
            char c = tenantId.charAt(i);
            if (c == ':' || Character.isISOControl(c)) {
                throw new IllegalStateException("Tenant id contains a colon or a control character and cannot be used in a cache key");
            }
        }
        return "@" + tenantId + ":";
    }
}
