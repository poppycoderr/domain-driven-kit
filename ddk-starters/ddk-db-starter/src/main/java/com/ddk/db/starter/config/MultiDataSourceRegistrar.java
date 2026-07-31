package com.ddk.db.starter.config;

import com.ddk.db.starter.properties.MultiDataSourceProperties;
import org.springframework.beans.factory.config.RuntimeBeanReference;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.GenericBeanDefinition;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.EnvironmentAware;
import org.springframework.context.annotation.ImportBeanDefinitionRegistrar;
import org.springframework.core.env.Environment;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.util.StringUtils;

import javax.sql.DataSource;

/**
 * 按 {@code ddk.datasource.sources} 配置动态注册多组
 * DataSource / PlatformTransactionManager / JdbcTemplate。
 *
 * <p>为什么用 {@link ImportBeanDefinitionRegistrar} 而不是
 * {@code BeanDefinitionRegistryPostProcessor}：后者会在配置属性绑定之前被实例化，
 * 因此不能有任何构造器注入依赖，否则容器启动阶段就会抛
 * {@code BeanInstantiationException: No default constructor found}。
 * Registrar 在配置类解析阶段执行，可以通过 {@link EnvironmentAware} 拿到
 * {@link Environment} 并用 {@link Binder} 手动绑定，不存在这个限制。
 *
 * @author Elijah Du
 */
public class MultiDataSourceRegistrar implements ImportBeanDefinitionRegistrar, EnvironmentAware {

    private static final String PREFIX = "ddk.datasource";

    private Environment environment;

    @Override
    public void setEnvironment(Environment environment) {
        this.environment = environment;
    }

    @Override
    public void registerBeanDefinitions(AnnotationMetadata metadata, BeanDefinitionRegistry registry) {
        MultiDataSourceProperties properties = Binder.get(environment)
                .bind(PREFIX, MultiDataSourceProperties.class)
                .orElseGet(MultiDataSourceProperties::new);

        if (properties.getSources() == null || properties.getSources().isEmpty()) {
            return;
        }

        String primary = properties.getPrimary();
        for (MultiDataSourceProperties.DataSourceProperty source : properties.getSources()) {
            if (!StringUtils.hasText(source.getName())) {
                throw new IllegalStateException(
                        PREFIX + ".sources[].name 不能为空，它决定了注册的 Bean 名称");
            }
            boolean isPrimary = source.getName().equals(primary);
            String dataSourceName = source.getName() + "DataSource";

            registry.registerBeanDefinition(dataSourceName, dataSource(source, isPrimary));
            registry.registerBeanDefinition(source.getName() + "TransactionManager",
                    derived(DataSourceTransactionManager.class, dataSourceName, isPrimary));
            registry.registerBeanDefinition(source.getName() + "JdbcTemplate",
                    derived(JdbcTemplate.class, dataSourceName, isPrimary));
        }
    }

    private GenericBeanDefinition dataSource(MultiDataSourceProperties.DataSourceProperty source,
                                             boolean isPrimary) {
        GenericBeanDefinition definition = new GenericBeanDefinition();
        definition.setBeanClass(DataSource.class);
        definition.setPrimary(isPrimary);
        // 连接池需要在容器关闭时释放，显式声明销毁方法
        definition.setDestroyMethodName("close");
        definition.setInstanceSupplier(() -> DataSourceBuilder.create()
                .type(source.getType())
                .url(source.getUrl())
                .username(source.getUsername())
                .password(source.getPassword())
                .driverClassName(source.getDriverClassName())
                .build());
        return definition;
    }

    /** 注册以某个 DataSource 为唯一构造参数的 Bean（事务管理器、JdbcTemplate）。 */
    private GenericBeanDefinition derived(Class<?> type, String dataSourceName, boolean isPrimary) {
        GenericBeanDefinition definition = new GenericBeanDefinition();
        definition.setBeanClass(type);
        definition.setPrimary(isPrimary);
        definition.getConstructorArgumentValues()
                .addGenericArgumentValue(new RuntimeBeanReference(dataSourceName));
        return definition;
    }
}
