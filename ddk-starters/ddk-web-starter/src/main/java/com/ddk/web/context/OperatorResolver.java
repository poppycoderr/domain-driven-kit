package com.ddk.web.context;

import com.ddk.core.context.Operator;
import jakarta.servlet.http.HttpServletRequest;
import org.jspecify.annotations.Nullable;

/**
 * 从请求里解析出当前操作者，由应用实现并声明为 Bean。
 * <p>
 * DDK 不知道应用怎么做认证：可以从 Spring Security 的上下文、网关透传的请求头或 JWT 里取。声明了这个 Bean 之后，
 * Web starter 会在每个请求期间把解析出的操作者放进 {@code OperatorContext}，请求结束时清除。
 */
@FunctionalInterface
public interface OperatorResolver {

    /**
     * @return 当前操作者；未登录的请求返回 {@code null}
     */
    @Nullable Operator resolve(HttpServletRequest request);
}
