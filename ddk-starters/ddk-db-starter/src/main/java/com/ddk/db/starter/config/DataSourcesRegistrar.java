package com.ddk.db.starter.config;

import org.springframework.beans.factory.config.RuntimeBeanReference;
import org.springframework.beans.factory.support.AbstractBeanDefinition;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.ConfigurationPropertyName;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.EnvironmentAware;
import org.springframework.context.annotation.ImportBeanDefinitionRegistrar;
import org.springframework.core.env.Environment;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.JdbcTransactionManager;
import org.springframework.util.StringUtils;

import javax.sql.DataSource;
import java.util.Map;

/**
 * 按 {@code ddk.datasource.sources} 为每个数据源注册 DataSource、事务管理器、JdbcTemplate。
 *
 * <h2>为什么是 ImportBeanDefinitionRegistrar</h2>
 * Bean 的数量和名字由配置决定，只能动态注册；而且必须在 Spring Boot 的
 * {@code DataSourceAutoConfiguration} 判断 {@code @ConditionalOnMissingBean(DataSource.class)} 之前注册完。
 * Registrar 在解析本自动配置类时执行，满足这两点。
 * {@code BeanDefinitionRegistryPostProcessor} 也能动态注册，但它在属性绑定之前实例化，拿不到配置。
 *
 * <h2>启动期校验</h2>
 * 多个数据源却没有指定主数据源、或主数据源名字写错，都会在启动时直接失败。
 * 否则容器能启动，直到第一次按类型注入 {@code DataSource} 才报「找到多个候选」，离配置错误很远。
 *
 * @author Elijah Du
 */
class DataSourcesRegistrar implements ImportBeanDefinitionRegistrar, EnvironmentAware {

    private Environment environment;

    @Override
    public void setEnvironment(Environment environment) {
        this.environment = environment;
    }

    @Override
    public void registerBeanDefinitions(AnnotationMetadata metadata, BeanDefinitionRegistry registry) {
        DdkDataSourceProperties properties = Binder.get(environment)
                .bind(DdkDataSourceProperties.PREFIX, DdkDataSourceProperties.class)
                .orElseGet(DdkDataSourceProperties::new);
        Binder binder = Binder.get(environment);
        Map<String, DdkDataSourceProperties.Source> sources = properties.getSources();
        if (sources.isEmpty()) {
            return;
        }
        String primary = resolvePrimary(properties.getPrimary(), sources);

        sources.forEach((name, source) -> {
            boolean isPrimary = name.equals(primary);
            String dataSourceBean = name + "DataSource";
            registry.registerBeanDefinition(dataSourceBean, dataSource(binder, name, source, isPrimary));
            registry.registerBeanDefinition(name + "TransactionManager",
                    derived(JdbcTransactionManager.class, dataSourceBean, isPrimary));
            registry.registerBeanDefinition(name + "JdbcTemplate",
                    derived(JdbcTemplate.class, dataSourceBean, isPrimary));
        });
    }

    static String resolvePrimary(String primary, Map<String, DdkDataSourceProperties.Source> sources) {
        if (!StringUtils.hasText(primary)) {
            if (sources.size() == 1) {
                return sources.keySet().iterator().next();
            }
            throw new IllegalStateException(DdkDataSourceProperties.PREFIX
                    + ".primary must be set when more than one data source is configured, candidates: "
                    + sources.keySet());
        }
        if (!sources.containsKey(primary)) {
            throw new IllegalStateException(DdkDataSourceProperties.PREFIX + ".primary '" + primary
                    + "' does not match any configured data source, candidates: " + sources.keySet());
        }
        return primary;
    }

    private static RootBeanDefinition dataSource(Binder binder, String name, DdkDataSourceProperties.Source source,
                                                 boolean isPrimary) {
        if (!StringUtils.hasText(source.getUrl())) {
            throw new IllegalStateException(DdkDataSourceProperties.PREFIX + ".sources." + name + ".url must be set");
        }
        // 登记具体的连接池类型，按 HikariDataSource 等实现类做类型查找时不必先实例化
        RootBeanDefinition definition = new RootBeanDefinition(poolType(source), () -> build(binder, name, source));
        definition.setPrimary(isPrimary);
        // 连接池有 close()，SimpleDriverDataSource 之类没有；按实际类型推断销毁方法
        definition.setDestroyMethodName(AbstractBeanDefinition.INFER_METHOD);
        return definition;
    }

    @SuppressWarnings("unchecked")
    private static Class<DataSource> poolType(DdkDataSourceProperties.Source source) {
        Class<? extends DataSource> type = source.getType() != null
                ? source.getType()
                : DataSourceBuilder.findType(DataSourcesRegistrar.class.getClassLoader());
        return (Class<DataSource>) (type != null ? type : DataSource.class);
    }

    private static DataSource build(Binder binder, String name, DdkDataSourceProperties.Source source) {
        DataSource dataSource = DataSourceBuilder.create()
                .type(source.getType())
                .url(source.getUrl())
                .username(source.getUsername())
                .password(source.getPassword())
                .driverClassName(source.getDriverClassName())
                .build();
        // 直接从 Environment 绑定到连接池实例，支持 maximum-pool-size / maximumPoolSize 等宽松写法
        binder.bind(ConfigurationPropertyName.adapt(
                DdkDataSourceProperties.PREFIX + ".sources." + name + ".pool", '.'), Bindable.ofInstance(dataSource));
        return dataSource;
    }

    private static RootBeanDefinition derived(Class<?> type, String dataSourceBean, boolean isPrimary) {
        RootBeanDefinition definition = new RootBeanDefinition(type);
        definition.setPrimary(isPrimary);
        definition.getConstructorArgumentValues().addGenericArgumentValue(new RuntimeBeanReference(dataSourceBean));
        return definition;
    }
}
