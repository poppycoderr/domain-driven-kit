package com.ddk.event.starter.internal;

import com.ddk.core.domain.IntegrationEvent;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.util.ClassUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 启动时检查应用包里所有 {@link IntegrationEvent} 的声明，让配置错误在启动期暴露，而不是等到提交后的投递才失败。
 * <p>
 * 投递发生在事务提交之后，那时再发现 key 写错，发布记录只能停在失败状态，等人修好代码后手工重投。
 * 同时检查（类型名，版本）不重复：两个类共用一个契约，消费方无法区分它们。
 */
public class IntegrationEventContractVerifier implements SmartInitializingSingleton {

    private final List<String> basePackages;

    private final IntegrationEventRouting routing;

    private final ClassLoader classLoader;

    public IntegrationEventContractVerifier(List<String> basePackages, IntegrationEventRouting routing, ClassLoader classLoader) {
        this.basePackages = basePackages;
        this.routing = routing;
        this.classLoader = classLoader;
    }

    @Override
    public void afterSingletonsInstantiated() {
        ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(IntegrationEvent.class));
        Map<String, String> contracts = new HashMap<>();
        for (String basePackage : basePackages) {
            for (BeanDefinition candidate : scanner.findCandidateComponents(basePackage)) {
                String className = candidate.getBeanClassName();
                if (className != null) {
                    verify(ClassUtils.resolveClassName(className, classLoader), contracts);
                }
            }
        }
    }

    void verify(Class<?> eventType, Map<String, String> contracts) {
        IntegrationEvent annotation = eventType.getAnnotation(IntegrationEvent.class);
        if (annotation == null) {
            return;
        }
        routing.validate(eventType, annotation);
        String contract = IntegrationEventRouting.typeOf(eventType, annotation) + " v" + annotation.version();
        String previous = contracts.putIfAbsent(contract, eventType.getName());
        if (previous != null && !previous.equals(eventType.getName())) {
            throw new IllegalStateException("Integration event contract " + contract + " is declared by both " + previous + " and " + eventType.getName());
        }
    }
}
