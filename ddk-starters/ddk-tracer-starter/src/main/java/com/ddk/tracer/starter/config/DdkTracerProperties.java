package com.ddk.tracer.starter.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 链路追踪配置。采样率、导出端点等仍由 Spring Boot 的 {@code management.tracing.*}、
 * {@code management.otlp.*} 管理，这里只放 DDK 自己的能力。
 *
 * @author Elijah Du
 */
@Data
@ConfigurationProperties(prefix = DdkTracerProperties.PREFIX)
public class DdkTracerProperties {

    public static final String PREFIX = "ddk.tracer";

    private final ResponseHeader responseHeader = new ResponseHeader();

    private final AsyncPropagation asyncPropagation = new AsyncPropagation();

    @Data
    public static class AsyncPropagation {

        /**
         * 是否让链路上下文跟随任务进入 Spring 的任务线程池。
         * <p>
         * 开启后 {@code @Async} 方法、以及事务提交后在线程池里投递的集成事件，都接在发起它们的那条链路上。
         * 应用自己声明了 {@code TaskDecorator} 时以应用为准。
         */
        private boolean enabled = true;
    }

    @Data
    public static class ResponseHeader {

        /**
         * 是否把当前请求的 traceId 写进 HTTP 响应头。
         * <p>
         * 调用方拿着这个值报障，就能直接在链路系统里定位到整条调用链，不用再按时间和接口去日志里捞。
         */
        private boolean enabled = true;

        /**
         * 响应头名称。浏览器端需要读取时，记得加进 {@code ddk.web.cors.exposed-headers}。
         */
        private String name = "X-Trace-Id";
    }
}
