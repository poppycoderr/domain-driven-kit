package com.ddk.db.starter.config;

import com.ddk.db.starter.properties.MultiDataSourceProperties;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;

/**
 * 多数据源自动配置。
 *
 * <p>仅当显式配置了 {@code ddk.datasource.primary} 时生效，避免与 Spring Boot 自带的
 * 单数据源自动配置产生歧义。实际的 Bean 注册工作由 {@link MultiDataSourceRegistrar} 完成。
 *
 * <p>配置示例：
 * <pre>{@code
 * ddk:
 *   datasource:
 *     primary: mainDb
 *     sources:
 *       - name: mainDb
 *         url: jdbc:mysql://localhost:3306/main
 *         username: root
 *         password: secret
 *         driver-class-name: com.mysql.cj.jdbc.Driver
 *         type: com.zaxxer.hikari.HikariDataSource
 *       - name: auditDb
 *         url: jdbc:mysql://localhost:3306/audit
 *         username: root
 *         password: secret
 *         driver-class-name: com.mysql.cj.jdbc.Driver
 *         type: com.zaxxer.hikari.HikariDataSource
 * }</pre>
 *
 * <p>每个 source 会注册三个 Bean：{@code <name>DataSource}、
 * {@code <name>TransactionManager}、{@code <name>JdbcTemplate}，
 * 其中与 {@code primary} 同名的那组被标记为 {@code @Primary}。
 *
 * @author Elijah Du
 */
@AutoConfiguration(before = DataSourceAutoConfiguration.class)
@ConditionalOnClass({DataSource.class, JdbcTemplate.class})
@ConditionalOnProperty(prefix = "ddk.datasource", name = "primary")
@EnableConfigurationProperties(MultiDataSourceProperties.class)
@Import(MultiDataSourceRegistrar.class)
public class MultiDataSourceAutoConfiguration {
}
