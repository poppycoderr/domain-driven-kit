package com.ddk.projection.starter.config;

import com.ddk.projection.starter.Projection;
import com.ddk.projection.starter.Projections;
import com.ddk.projection.starter.internal.JdbcProjections;
import com.ddk.projection.starter.internal.MicrometerRefreshMetrics;
import com.ddk.projection.starter.internal.PendingRefreshes;
import com.ddk.projection.starter.internal.RefreshMetrics;
import com.ddk.projection.starter.internal.RefreshWorker;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;

import java.time.Clock;
import java.util.List;

/**
 * 读模型投影自动配置：应用里声明了 {@link Projection} 时，注册待刷新表、后台处理和 {@link Projections} 入口。
 *
 * @author Elijah Du
 */
@AutoConfiguration(afterName = {
        "org.springframework.boot.jdbc.autoconfigure.JdbcTemplateAutoConfiguration",
        "org.springframework.boot.jdbc.autoconfigure.DataSourceTransactionManagerAutoConfiguration"
})
@ConditionalOnClass(JdbcTemplate.class)
@ConditionalOnBean({Projection.class, JdbcTemplate.class, PlatformTransactionManager.class})
@ConditionalOnProperty(prefix = DdkProjectionProperties.PREFIX, name = "enabled", matchIfMissing = true)
@EnableConfigurationProperties(DdkProjectionProperties.class)
public class DdkProjectionAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    PendingRefreshes ddkPendingRefreshes(JdbcTemplate jdbc, PlatformTransactionManager transactionManager, DdkProjectionProperties properties) {
        properties.validate();
        PendingRefreshes pending = new PendingRefreshes(jdbc, transactionManager, properties.getTable(), Clock.systemUTC());
        pending.prepare(properties.isInitializeSchema());
        return pending;
    }

    @Bean
    @ConditionalOnMissingBean
    RefreshWorker ddkRefreshWorker(List<Projection> projections, PendingRefreshes pending, DdkProjectionProperties properties,
            ObjectProvider<RefreshMetrics> metrics) {
        return new RefreshWorker(projections, pending, properties.getBatchSize(), properties.getSweepInterval(),
                properties.getRetryDelay(), properties.getMaxRetryDelay(), properties.getLeaseTime(), metrics.getIfAvailable(() -> RefreshMetrics.NONE));
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(MeterRegistry.class)
    static class MetricsConfiguration {

        @Bean
        @ConditionalOnMissingBean
        RefreshMetrics ddkRefreshMetrics(ObjectProvider<MeterRegistry> registry) {
            return new MicrometerRefreshMetrics(registry);
        }
    }

    @Bean
    @ConditionalOnMissingBean
    Projections projections(List<Projection> projections, PendingRefreshes pending, RefreshWorker worker) {
        return new JdbcProjections(projections, pending, worker);
    }
}
