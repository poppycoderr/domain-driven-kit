package com.ddk.web.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

import java.time.Duration;
import java.util.List;

/**
 * Web 层配置。
 *
 * @author Elijah Du
 */
@Data
@ConfigurationProperties(prefix = DdkWebProperties.PREFIX)
public class DdkWebProperties {

    public static final String PREFIX = "ddk.web";

    /**
     * 是否注册全局异常处理器。
     * <p>
     * 关掉之后 {@code BusinessException} 等异常会走 Spring Boot 默认的错误页，
     * 响应结构不再是 {@code ApiResponse}。
     */
    private boolean exceptionHandler = true;

    /**
     * 是否让 DDK 调整 Jackson 的默认行为。
     * <p>
     * 启用时以 {@code Jackson2ObjectMapperBuilderCustomizer} 的形式参与构建，
     * 而不是替换整个 {@code ObjectMapper} Bean——这样 {@code spring.jackson.*}
     * 的配置仍然生效，使用方的定制不会被悄悄吃掉。
     */
    private boolean jackson = true;

    /**
     * 是否把 {@code Long} / {@code long} 序列化成 JSON 字符串，仅在 {@code ddk.web.jackson} 启用时生效。
     * <p>
     * JavaScript 的 Number 只能精确表示 2^53 以内的整数，雪花 ID 远超这个范围，
     * 前端解析后会被静默改成另一个值。默认开启，代价是所有 64 位整数（包括计数）都以字符串返回。
     */
    private boolean writeLongAsString = true;

    @NestedConfigurationProperty
    private Cors cors = new Cors();

    /**
     * 跨域配置。
     * <p>
     * <b>默认关闭。</b>早期版本默认放开了所有来源、所有请求头并允许携带凭证，
     * 这对一个库来说是危险的默认值——使用方往往根本不知道自己的服务被开了全域跨域。
     * 需要跨域就显式打开并列出来源。
     */
    @Data
    public static class Cors {

        /** 是否注册跨域配置 */
        private boolean enabled = false;

        /** 生效的路径模式 */
        private String pathPattern = "/**";

        /**
         * 允许的来源，支持通配符模式（对应 {@code allowedOriginPatterns}）。
         * <p>
         * 允许携带凭证时不要写 {@code *}：那等于让任意站点带着用户的 Cookie 调你的接口。
         */
        private List<String> allowedOrigins = List.of();

        /** 允许的方法 */
        private List<String> allowedMethods = List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS");

        /** 允许的请求头 */
        private List<String> allowedHeaders = List.of("*");

        /** 暴露给浏览器的响应头 */
        private List<String> exposedHeaders = List.of();

        /** 是否允许携带凭证（Cookie / Authorization） */
        private boolean allowCredentials = false;

        /** 预检请求的缓存时长 */
        private Duration maxAge = Duration.ofHours(1);
    }
}
