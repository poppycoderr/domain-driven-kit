package com.example.mall.platform;

import com.ddk.core.context.Operator;
import com.ddk.core.context.OperatorContext;
import com.ddk.core.exception.BusinessException;
import com.ddk.web.context.OperatorResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.regex.Pattern;

/**
 * 顾客身份。
 * <ul>
 *     <li>默认 profile（开发模式）：不做认证，网关或前端在请求头 {@value #HEADER} 里传入顾客 ID，这里把它解析成操作者。方便本地运行，但这个头谁都能伪造；</li>
 *     <li>{@code secure} profile：操作者取自 Spring Security 的认证结果，请求头被忽略，见 {@link MallSecurity}。</li>
 * </ul>
 * 应用服务只看 {@code OperatorContext}，两种模式下的业务代码完全一样。
 */
@Configuration(proxyBeanMethods = false)
public class CustomerIdentity {

    public static final String HEADER = "X-Customer-Id";

    private static final Pattern CUSTOMER_ID = Pattern.compile("[1-9]\\d{0,17}");

    @Bean
    @Profile("!" + MallSecurity.PROFILE)
    OperatorResolver headerOperatorResolver() {
        return request -> {
            String customerId = request.getHeader(HEADER);
            return customerId == null || !CUSTOMER_ID.matcher(customerId).matches() ? null : Operator.of(customerId);
        };
    }

    @Bean
    @Profile(MallSecurity.PROFILE)
    OperatorResolver authenticatedOperatorResolver() {
        return request -> {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            boolean signedIn = authentication != null && authentication.isAuthenticated() && !(authentication instanceof AnonymousAuthenticationToken);
            return signedIn ? Operator.of(authentication.getName()) : null;
        };
    }

    /**
     * 当前请求的顾客 ID。
     *
     * @throws BusinessException 请求没有顾客身份（没有登录、没有带合法的请求头，或者登录的是客服）
     */
    public static Long currentCustomerId() {
        return OperatorContext.current()
                .map(Operator::id)
                .filter(id -> CUSTOMER_ID.matcher(id).matches())
                .map(Long::valueOf)
                .orElseThrow(() -> new BusinessException(PlatformError.CUSTOMER_REQUIRED));
    }
}
