package com.ddk.projection.starter.config;

import com.ddk.projection.starter.Projection;
import com.ddk.projection.starter.Projections;
import com.ddk.projection.starter.internal.PendingRefreshes;
import com.ddk.projection.starter.internal.RefreshWorker;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
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
        PendingRefreshes pending = new PendingRefreshes(jdbc, transactionManager, properties.getTable(), Clock.systemUTC());
        if (properties.isInitializeSchema()) {
            jdbc.execute(pending.schema());
        }
        return pending;
    }

    @Bean
    @ConditionalOnMissingBean
    RefreshWorker ddkRefreshWorker(List<Projection> projections, PendingRefreshes pending, DdkProjectionProperties properties) {
        return new RefreshWorker(projections, pending, properties.getBatchSize(), properties.getSweepInterval(),
                properties.getRetryDelay(), properties.getMaxRetryDelay());
    }

    @Bean
    @ConditionalOnMissingBean
    Projections projections(List<Projection> projections, PendingRefreshes pending, RefreshWorker worker) {
        return new Projections(projections, pending, worker);
    }
}
