package com.ddk.concurrency.starter.internal;

import java.lang.reflect.Method;
import java.time.Duration;
import java.util.Map;
import java.util.TreeMap;

/** Reaches the package-private scope and key names of @Idempotent and @RateLimit, exactly as the released version computes them. */
public final class KeyProbe {

    private KeyProbe() {
    }

    public static Map<String, Object> keys(Method method, String requestKey, String rateKey, long limit, Duration period) {
        String scope = ConcurrencyInterceptor.defaultScope(method);
        Map<String, Object> keys = new TreeMap<>();
        keys.put("defaultScope", scope);
        keys.put("idempotentKey", new RequestRegistry(null, "ddk:").name(scope, requestKey));
        keys.put("rateKey", new RateLimiters(null, "ddk:").name(scope, rateKey, limit, period));
        return keys;
    }
}
