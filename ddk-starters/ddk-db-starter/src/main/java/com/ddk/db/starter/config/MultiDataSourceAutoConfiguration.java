package com.ddk.db.starter.config;

import com.ddk.db.starter.properties.MultiDataSourceProperties;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.config.RuntimeBeanReference;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor;
import org.springframework.beans.factory.support.GenericBeanDefinition;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;

@AutoConfiguration
@EnableConfigurationProperties(MultiDataSourceProperties.class)
// Ensure configuration is active only if sources are defined.
// Checking for primary is a good proxy, or sources[0].url for more directness.
@ConditionalOnProperty(prefix = "ddk.datasource", name = "primary", matchIfMissing = false)
// A more robust check could be:
// @ConditionalOnProperty(prefix = "ddk.datasource.sources[0]", name = "url", matchIfMissing = false)
public class MultiDataSourceAutoConfiguration implements BeanDefinitionRegistryPostProcessor {

    private final MultiDataSourceProperties multiDataSourceProperties;

    public MultiDataSourceAutoConfiguration(MultiDataSourceProperties multiDataSourceProperties) {
        this.multiDataSourceProperties = multiDataSourceProperties;
    }

    @Override
    public void postProcessBeanDefinitionRegistry(BeanDefinitionRegistry registry) throws BeansException {
        if (multiDataSourceProperties.getSources() == null || multiDataSourceProperties.getSources().isEmpty()) {
            return;
        }

        for (MultiDataSourceProperties.DataSourceProperty sourceProperty : multiDataSourceProperties.getSources()) {
            // 1. Register DataSource bean
            GenericBeanDefinition dsBeanDefinition = new GenericBeanDefinition();
            dsBeanDefinition.setBeanClass(DataSource.class); // Target type
            // Use a supplier for DataSourceBuilder to create the instance
            dsBeanDefinition.setInstanceSupplier(() ->
                    DataSourceBuilder.create()
                            .type(sourceProperty.getType())
                            .url(sourceProperty.getUrl())
                            .username(sourceProperty.getUsername())
                            .password(sourceProperty.getPassword())
                            .driverClassName(sourceProperty.getDriverClassName())
                            .build()
            );

            if (sourceProperty.getName().equals(multiDataSourceProperties.getPrimary())) {
                dsBeanDefinition.setPrimary(true);
            }
            String dataSourceBeanName = sourceProperty.getName() + "DataSource";
            registry.registerBeanDefinition(dataSourceBeanName, dsBeanDefinition);

            // 2. Register PlatformTransactionManager bean
            GenericBeanDefinition txManagerDefinition = new GenericBeanDefinition();
            txManagerDefinition.setBeanClass(DataSourceTransactionManager.class);
            txManagerDefinition.getConstructorArgumentValues().addGenericArgumentValue(new RuntimeBeanReference(dataSourceBeanName));

            if (sourceProperty.getName().equals(multiDataSourceProperties.getPrimary())) {
                txManagerDefinition.setPrimary(true);
            }
            String txManagerBeanName = sourceProperty.getName() + "TransactionManager";
            registry.registerBeanDefinition(txManagerBeanName, txManagerDefinition);

            // 3. Register JdbcTemplate bean
            GenericBeanDefinition jdbcTemplateDefinition = new GenericBeanDefinition();
            jdbcTemplateDefinition.setBeanClass(JdbcTemplate.class);
            jdbcTemplateDefinition.getConstructorArgumentValues().addGenericArgumentValue(new RuntimeBeanReference(dataSourceBeanName));

            if (sourceProperty.getName().equals(multiDataSourceProperties.getPrimary())) {
                jdbcTemplateDefinition.setPrimary(true);
            }
            String jdbcTemplateBeanName = sourceProperty.getName() + "JdbcTemplate";
            registry.registerBeanDefinition(jdbcTemplateBeanName, jdbcTemplateDefinition);
        }
    }

    @Override
    public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException {
        // No specific post-processing of bean factory needed for this configuration
    }
}
