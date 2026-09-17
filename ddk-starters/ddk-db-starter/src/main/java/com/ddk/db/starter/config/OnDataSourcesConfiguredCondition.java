package com.ddk.db.starter.config;

import org.springframework.boot.autoconfigure.condition.ConditionOutcome;
import org.springframework.boot.autoconfigure.condition.SpringBootCondition;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;

import java.util.Map;

/**
 * 配置了至少一个 {@code ddk.datasource.sources.<name>} 时匹配。
 *
 * @author Elijah Du
 */
class OnDataSourcesConfiguredCondition extends SpringBootCondition {

    @Override
    public ConditionOutcome getMatchOutcome(ConditionContext context, AnnotatedTypeMetadata metadata) {
        boolean configured = Binder.get(context.getEnvironment())
                .bind(DdkDataSourceProperties.PREFIX + ".sources", Bindable.mapOf(String.class, Object.class))
                .map(Map::isEmpty)
                .map(empty -> !empty)
                .orElse(false);
        return configured
                ? ConditionOutcome.match("ddk.datasource.sources is configured")
                : ConditionOutcome.noMatch("ddk.datasource.sources is empty");
    }
}
