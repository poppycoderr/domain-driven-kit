package com.example.inventory.adapter.web;

import com.ddk.core.context.Operator;
import com.ddk.web.context.OperatorResolver;
import jakarta.servlet.http.HttpServletRequest;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

/**
 * 销售渠道作为租户：请求头 {@value #HEADER} 里的渠道成为操作者和它的租户。
 * <p>
 * 只用于演示，这个头谁都能伪造；真实的应用从认证结果（令牌里的租户声明）里取。它在任何 profile 下都注册，
 * 因为原生镜像的 Bean 在构建时就定下来了；只有 {@code tenant} profile 打开了按租户区分的 key，默认 profile 下租户不影响任何 key。
 */
@Component
public class SalesChannelResolver implements OperatorResolver {

    public static final String HEADER = "X-Sales-Channel";

    private static final Pattern CHANNEL = Pattern.compile("[a-z0-9-]{1,32}");

    @Override
    public @Nullable Operator resolve(HttpServletRequest request) {
        String channel = request.getHeader(HEADER);
        return channel == null || !CHANNEL.matcher(channel).matches() ? null : Operator.of(channel, channel);
    }
}
