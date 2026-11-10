package com.ddk.seata.starter.http;

import org.apache.seata.core.context.RootContext;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.util.StringUtils;

import java.io.IOException;

/**
 * 出站 HTTP 调用时，把当前线程绑定的全局事务 XID 写进 {@code TX_XID} 请求头。
 * <p>
 * 下游服务由 Seata 自带的入站拦截器读取同名请求头并绑定 XID，两端合起来事务上下文才能跨服务传播。
 * 不在全局事务中时什么都不做；调用方已经显式设置了该头时不覆盖。
 *
 * @author Elijah Du
 *
 * @deprecated 0.12.0 起弃用，1.0 不再发布本 starter，也会把它移出 {@code ddk-dependencies}。Seata 的运行时没有在 Spring Boot 4 上验证过，
 * 全局事务也和 DDK 推荐的发件箱加最终一致性方向相反。需要时固定在 0.12.x，或者把这个 starter 的三个类复制到项目里。
 */
@Deprecated(since = "0.12.0", forRemoval = true)
public class XidPropagationInterceptor implements ClientHttpRequestInterceptor {

    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body, ClientHttpRequestExecution execution)
            throws IOException {
        String xid = RootContext.getXID();
        if (StringUtils.hasText(xid) && !request.getHeaders().containsHeader(RootContext.KEY_XID)) {
            request.getHeaders().set(RootContext.KEY_XID, xid);
        }
        return execution.execute(request, body);
    }
}
