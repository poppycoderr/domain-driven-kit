package com.ddk.seata.starter.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * DDK 对 Seata 的补充配置。事务分组、注册中心、TC 地址等仍由 Seata 自己的 {@code seata.*} 管理。
 *
 * @author Elijah Du
 *
 * @deprecated 0.12.0 起弃用，1.0 不再发布本 starter，也会把它移出 {@code ddk-dependencies}。Seata 的运行时没有在 Spring Boot 4 上验证过，
 * 全局事务也和 DDK 推荐的发件箱加最终一致性方向相反。需要时固定在 0.12.x，或者把这个 starter 的三个类复制到项目里。
 */
@Deprecated(since = "0.12.0", forRemoval = true)
@Data
@ConfigurationProperties(prefix = DdkSeataProperties.PREFIX)
public class DdkSeataProperties {

    public static final String PREFIX = "ddk.seata";

    private final HttpPropagation httpPropagation = new HttpPropagation();

    @Data
    public static class HttpPropagation {

        /**
         * 是否在出站 HTTP 调用上携带全局事务 XID。
         * <p>
         * Seata 只处理了入站（从请求头取 XID 绑定到当前线程），出站调用不带 XID，
         * 下游服务就会在全局事务之外各自提交，回滚时无法一起回滚。
         */
        private boolean enabled = true;
    }
}
