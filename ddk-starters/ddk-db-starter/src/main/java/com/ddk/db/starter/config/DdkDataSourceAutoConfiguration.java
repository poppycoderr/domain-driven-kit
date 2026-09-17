package com.ddk.db.starter.config;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;

/**
 * 多数据源自动配置。
 * <p>
 * 配置了 {@code ddk.datasource.sources} 时生效，排在 Spring Boot 的 {@link DataSourceAutoConfiguration} 之前：
 * 本 starter 注册的主数据源、事务管理器、JdbcTemplate 让 Boot 的单数据源配置整体退让。
 * 没有配置时什么都不做，由 {@code spring.datasource.*} 照常工作。
 *
 * @author Elijah Du
 */
@AutoConfiguration(before = DataSourceAutoConfiguration.class)
@ConditionalOnClass({DataSource.class, JdbcTemplate.class})
@Conditional(OnDataSourcesConfiguredCondition.class)
@EnableConfigurationProperties(DdkDataSourceProperties.class)
@Import(DataSourcesRegistrar.class)
public class DdkDataSourceAutoConfiguration {
}
