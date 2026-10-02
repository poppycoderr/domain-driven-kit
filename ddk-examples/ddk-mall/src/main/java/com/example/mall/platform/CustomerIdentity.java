package com.example.mall.platform;

import com.ddk.core.context.Operator;
import com.ddk.core.context.OperatorContext;
import com.ddk.core.exception.BusinessException;
import com.ddk.web.context.OperatorResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 顾客身份。示例不做登录认证：网关或前端在请求头 {@value #HEADER} 里传入顾客 ID，这里把它解析成操作者。
 * 真实项目里换成从 Spring Security 的上下文或 JWT 取值，其余代码不用改。
 */
@Configuration(proxyBeanMethods = false)
public class CustomerIdentity {

    public static final String HEADER = "X-Customer-Id";

    @Bean
    OperatorResolver operatorResolver() {
        return request -> {
            String customerId = request.getHeader(HEADER);
            return customerId == null || !customerId.matches("[1-9]\\d{0,17}") ? null : Operator.of(customerId);
        };
    }

    /**
     * 当前请求的顾客 ID。
     *
     * @throws BusinessException 请求没有带合法的顾客身份
     */
    public static Long currentCustomerId() {
        return OperatorContext.current()
                .map(operator -> Long.valueOf(operator.id()))
                .orElseThrow(() -> new BusinessException(PlatformError.CUSTOMER_REQUIRED));
    }
}
