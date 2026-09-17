package com.ddk.db.starter.config;

import lombok.Data;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.LinkedHashMap;
import java.util.Map;
import javax.sql.DataSource;

/**
 * 多数据源配置。
 * <pre>{@code
 * ddk:
 *   datasource:
 *     primary: main
 *     sources:
 *       main:
 *         url: jdbc:mysql://localhost:3306/main
 *         username: app
 *         password: secret
 *         pool:
 *           maximum-pool-size: 20
 *       audit:
 *         url: jdbc:mysql://localhost:3306/audit
 * }</pre>
 *
 * @author Elijah Du
 */
@Data
@ConfigurationProperties(prefix = DdkDataSourceProperties.PREFIX)
public class DdkDataSourceProperties {

    public static final String PREFIX = "ddk.datasource";

    /**
     * 主数据源的名字，它的 DataSource、事务管理器、JdbcTemplate 标记为 {@code @Primary}。
     * 只有一个数据源时可以省略。
     */
    private @Nullable String primary;

    /**
     * 数据源，key 是名字，决定注册的 Bean 名：{@code <name>DataSource}、
     * {@code <name>TransactionManager}、{@code <name>JdbcTemplate}。
     */
    private Map<String, Source> sources = new LinkedHashMap<>();

    @Data
    public static class Source {

        /**
         * JDBC URL。
         */
        private @Nullable String url;

        private @Nullable String username;

        private @Nullable String password;

        /**
         * JDBC 驱动类名，通常可以从 URL 推断。
         */
        private @Nullable String driverClassName;

        /**
         * 连接池实现，默认按 classpath 选择（通常是 HikariCP）。
         */
        private @Nullable Class<? extends DataSource> type;

        /**
         * 连接池自身的属性，按名字绑定到连接池实例上，
         * 例如 HikariCP 的 {@code maximum-pool-size}、{@code connection-timeout}、{@code pool-name}。
         */
        private Map<String, String> pool = new LinkedHashMap<>();
    }
}
