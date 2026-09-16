package com.ddk.cache.starter.config;

import org.springframework.boot.autoconfigure.condition.ConditionOutcome;
import org.springframework.boot.autoconfigure.condition.SpringBootCondition;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;

/**
 * 只有至少一个缓存启用了 L1 且没有关闭广播时，才值得占用一条 Redis 订阅连接。
 *
 * @author Elijah Du
 */
class OnBroadcastEvictCondition extends SpringBootCondition {

    @Override
    public ConditionOutcome getMatchOutcome(ConditionContext context, AnnotatedTypeMetadata metadata) {
        DdkCacheProperties properties = Binder.get(context.getEnvironment())
                .bind(DdkCacheProperties.PREFIX, DdkCacheProperties.class)
                .orElseGet(DdkCacheProperties::new);
        if (!properties.getLocal().isBroadcastEvict()) {
            return ConditionOutcome.noMatch("ddk.cache.local.broadcast-evict is false");
        }
        if (!properties.anyLocalEnabled()) {
            return ConditionOutcome.noMatch("no cache enables the local level");
        }
        return ConditionOutcome.match("a cache enables the local level");
    }
}
