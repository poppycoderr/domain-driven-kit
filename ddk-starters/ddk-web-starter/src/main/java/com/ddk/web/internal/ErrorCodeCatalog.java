package com.ddk.web.internal;

import com.ddk.core.exception.CommonError;
import com.ddk.core.exception.ErrorCode;
import org.springframework.beans.factory.annotation.AnnotatedBeanDefinition;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AssignableTypeFilter;
import org.springframework.util.ClassUtils;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 收集应用里的错误码：DDK 的 {@link CommonError}，加上应用包下所有实现了 {@link ErrorCode} 的枚举。
 * <p>
 * 错误码按领域分散定义在各自的枚举里，这里只在生成接口文档时把它们汇总成一份清单。不是枚举的 {@code ErrorCode} 实现没有可列举的取值，不收集。
 */
public final class ErrorCodeCatalog {

    private ErrorCodeCatalog() {
    }

    /**
     * @return 错误码到消息模板的映射，框架的在前，应用的按枚举类名排序
     */
    public static Map<String, String> scan(Collection<String> basePackages, ClassLoader classLoader) {
        Map<String, String> codes = new LinkedHashMap<>();
        add(codes, CommonError.class);

        ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(false) {

            @Override
            protected boolean isCandidateComponent(AnnotatedBeanDefinition definition) {
                return true;
            }
        };
        scanner.addIncludeFilter(new AssignableTypeFilter(ErrorCode.class));
        basePackages.stream()
                .flatMap(basePackage -> scanner.findCandidateComponents(basePackage).stream())
                .map(BeanDefinition::getBeanClassName)
                .filter(name -> name != null)
                .sorted()
                .map(name -> ClassUtils.resolveClassName(name, classLoader))
                .filter(Class::isEnum)
                .forEach(type -> add(codes, type));
        return codes;
    }

    private static void add(Map<String, String> codes, Class<?> enumType) {
        for (Object constant : enumType.getEnumConstants()) {
            ErrorCode code = (ErrorCode) constant;
            codes.putIfAbsent(code.getCode(), code.getMessage());
        }
    }
}
