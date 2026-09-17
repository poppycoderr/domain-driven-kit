package com.ddk.seata.starter.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * DDK 对 Seata 的补充配置。事务分组、注册中心、TC 地址等仍由 Seata 自己的 {@code seata.*} 管理。
 *
 * @author Elijah Du
 */
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
